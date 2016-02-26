package org.imsi.badimsibox.badimsiserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import java.util.logging.Level;

/**
 *
 * @author AisukoWasTaken AlisterWan TTAK WarenUT Andrebreton
 */
public class BadIMSIService extends AbstractVerticle {

    static String defaultHeaders = "Origin, X-Requested-With, Content-Type, Accept";
    static String defaultMethods = "GET, POST, OPTIONS, PUT, HEAD, DELETE, CONNECT";
    static String defaultIpAndPorts = "*";

    private final PythonManager pythonManager = new PythonManager();
    private final ArrayList<Bts> operatorList = new ArrayList<>();
    private final ArrayList<String> command = new ArrayList<>();

    private Session currentSession = Session.init(vertx);
    private SynchronousThreadManager smsThreadManager = null;
    private SynchronousThreadManager timsiThreadManager = null;

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(rc -> {
            rc.response().putHeader("Access-Control-Allow-Headers", defaultHeaders);
            rc.response().putHeader("Access-Control-Allow-Methods", defaultMethods);
            rc.response().putHeader("Access-Control-Allow-Origin", defaultIpAndPorts);
            rc.next();
        });

        // Sessions
        router.get("/master/session/start/:password").handler(this::startSession);
        router.get("/master/session/state").handler(this::getSessionState);
        router.get("/master/session/set/state/:state").handler(this::setSessionState);
        router.route("/master/session/destroy/").handler(this::destroySession);

        // Sniffing
        router.post("/master/sniffing/selectOperator/").handler(this::selectOperatorToSniff);
        router.post("/master/sniffing/selectBand/").handler(this::selectBandToSniff);
        router.post("/master/sniffing/start/").handler(this::startSniffing);

        // OpenBTS
        router.post("/master/fakebts/start/").handler(this::startOpenBTS);
        router.post("/master/fakebts/selectOperator/").handler(this::selectOperatorToSpoof);
        router.route("/master/fakebts/getBTSList/").handler(this::getBTSList);
        //router.route("/master/fakebts/timsis/").handler(this::launchTimsiReceptor);
        router.post("/master/fakebts/stop/").handler(this::stopOpenBTS);

        // Jamming
        router.post("/master/jamming/start/").handler(this::startJamming);
        router.route("/master/jamming/operator/").handler(this::selectOperatorToJamm);
        router.get("/master/jamming/stop").handler(this::stopJamming);

        // SMS
        router.post("/master/attack/sms/send/").handler(this::sendSMS);
        router.route("/master/attack/sms/receive/").handler(this::launchSmsReceptor);

        // Handle EventBus
        router.route("/eventbus/*").handler(SockJSHandler.create(vertx).bridge(new BridgeOptions().addOutboundPermitted(new PermittedOptions())));
        router.route().handler(StaticHandler.create());
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    /**
     *
     * @param rc
     */
    private void startSession(RoutingContext rc) {
        String password = rc.request().getParam("password");
        this.currentSession = new Session(password, vertx);
        this.currentSession.updateTimestamp();
        rc.response().putHeader("content-type", "application/json").end(
                new JsonObject().put("started", true).encode()
        );
    }

    /**
     *
     * @param rc
     */
    private void getSessionState(RoutingContext rc) {
        if (this.currentSession == null) {
            rc.response().putHeader("content-type", "application/json").end(
                    new JsonObject().put("state", -1).encode()
            );
        } else {
            rc.response().putHeader("content-type", "application/json").end(
                    new JsonObject()
                    .put("state", this.currentSession.getSessionState())
                    .put("password", this.currentSession.getPassword())
                    .put("timestamp", this.currentSession.getTimestamp())
                    .encode()
            );
        }
    }

    /**
     *
     * @param rc
     */
    private void setSessionState(RoutingContext rc) {
        String stateString = rc.request().getParam("state");
        int stateInt = Integer.parseInt(stateString);
        boolean stateChanged = (stateInt > -1) && (stateInt < 5)
                && (this.currentSession.getSessionState() < stateInt);
        if (stateChanged) {
            this.currentSession.nextSessionState();
        }
        rc.response().putHeader("content-type", "application/json").end(
                new JsonObject().put("stateChanged", stateChanged).encode()
        );
    }

    /**
     *
     * @param rc
     */
    private void destroySession(RoutingContext rc) {
        currentSession = Session.init(vertx);
        if (smsThreadManager != null) {
            smsThreadManager.stop();
        }
    }

    /**
     *
     * @param rc
     */
    private void selectOperatorToSniff(RoutingContext rc) {
        rc.request().bodyHandler(h -> {
            JsonObject reqJson = formatJsonParams(h);
            String operator = reqJson.getString("operator");
            JsonObject json = new JsonObject();
            json.put("operator", operator);
            this.vertx.eventBus().publish("observer.new", json.encode());
            rc.response().putHeader("content-type", "application/json").end(
                    new JsonObject().put("selectReceived", true).encode()
            );
        });
    }

    /**
     *
     * @param rc
     */
    private void selectBandToSniff(RoutingContext rc) {
        rc.request().bodyHandler(h -> {
            JsonObject reqJson = formatJsonParams(h);

            String operator = reqJson.getString("band");
            JsonObject json = new JsonObject();
            json.put("band", operator);
            this.vertx.eventBus().publish("observer.new", json.encode());
            rc.response().putHeader("content-type", "application/json").end(
                    new JsonObject().put("selectReceived", true).encode()
            );
        });
    }

    /**
     *
     * @param rc
     */
    private void startSniffing(RoutingContext rc) {
        rc.request().bodyHandler(h -> {
            JsonObject reqJson = formatJsonParams(h);
            signalObserverForStartingLongTreatment();

            operatorList.clear();

            command.clear();
            command.add("badimsicore_listen");
            command.add("-o");
            command.add(reqJson.getString("operator"));
            String band = reqJson.getString("band");
            if (band != null && !band.equalsIgnoreCase("")) {
                command.add("-b");
                command.add(band);
            }

            // Blocking code
            vertx.executeBlocking(future -> {
                launchAndWait(future);
            }, res -> {
                JsonObject answer = new JsonObject();
                if (res.succeeded()) {
                    Process p = (Process) res.result();
                    BufferedReader br
                            = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    br.lines().filter(line -> line.startsWith("->"))
                            .map(line -> line.split(","))
                            .forEach((tab) -> {
                                List<String> arfcns = new ArrayList<>(tab.length - 4);
                                for (int i = 4; i < tab.length; i++) {
                                    arfcns.add(tab[i]);
                                }
                                operatorList.add(new Bts(tab[0].split(" ")[1], tab[1], tab[2], tab[3], arfcns));
                            });
                    answer.put("status", "data");
                    JsonArray array = extractAndFormatBtsList();
                    answer.put("content", array);
                } else {
                    answer.put("status", "error");
                    answer.put("content", res.cause().getMessage());
                }
                this.vertx.eventBus().publish("observer.new", answer.encode());
                rc.response().putHeader("content-type", "application/json").end(
                        answer.encode()
                );
            });
        });
    }

    /**
     *
     * @param rc
     */
    private void startJamming(RoutingContext rc) {
        command.clear();
        command.add("jamming");
        command.add("start");

        vertx.executeBlocking(future -> {
            launchAndWait(future);
        }, res -> {
            JsonObject answer = new JsonObject();
            answer.put("started", res.succeeded());
            if (res.failed()) {
                answer.put("error", res.cause().getMessage());
            }
            rc.response().putHeader("content-type", "application/json").end(
                    answer.encode()
            );
        });
    }

    /**
     *
     * @param rc
     */
    private void selectOperatorToJamm(RoutingContext rc) {
        JsonArray array = extractAndFormatBtsList();
        rc.response().putHeader("content-type", "application/json").end(
                array.encode()
        );
    }

    /**
     *
     * @param rc
     */
    private void stopJamming(RoutingContext rc) {
        command.clear();
        command.add("jamming");
        command.add("stop");

        vertx.executeBlocking(future -> {
            launchAndWait(future);
        }, res -> {
            JsonObject answer = new JsonObject();
            answer.put("stopped", res.succeeded());
            if (res.failed()) {
                answer.put("error", res.cause().getMessage());
            }
            rc.response().putHeader("content-type", "application/json").end(
                    answer.encode()
            );
        });
    }

    /**
     *
     * @param rc
     */
    private void startOpenBTS(RoutingContext rc) {
        rc.request().bodyHandler(h -> {
            JsonObject reqJson = formatJsonParams(h);
            String ci = reqJson.getString("CI");
            Bts selectedOperator = null;

            for (Bts operator : operatorList) {
                if (operator.getCi().equals(ci)) {
                    selectedOperator = operator;
                    break;
                }
            }

            // What do we do if selectedOperator is null ? It is possible ?
            command.clear();
            command.add("badimsicore_openbts");
            command.add("start");
            command.add("-i");
            command.add(selectedOperator.getCi());
            command.add("-l");
            command.add(selectedOperator.getLac());
            command.add("-n");
            command.add(selectedOperator.getOperator().getMnc());
            command.add("-c");
            command.add(selectedOperator.getOperator().getMcc());
            command.add("-p");
            command.add("\".*\"");

            vertx.executeBlocking(future -> {
                launchAndWait(future);
            }, res -> {
                JsonObject answer = new JsonObject();
                answer.put("started", res.succeeded());
                if (res.failed()) {
                    answer.put("error", res.cause().getMessage());
                } else {
                    launchTimsiReceptor(rc);
                }
                rc.response().putHeader("content-type", "application/json").end(
                        answer.encode()
                );
            });
        });
    }

    /**
     *
     * @param rc
     */
    private void getBTSList(RoutingContext rc) {
        JsonArray array = extractAndFormatBtsList();
        rc.response().putHeader("content-type", "application/json").end(
                array.encode()
        );
    }

    /**
     *
     * @param rc
     */
    private void selectOperatorToSpoof(RoutingContext rc) {
        rc.request().bodyHandler(h -> {
            JsonObject reqJson = formatJsonParams(h);
            String operator = reqJson.getString("operator");
            JsonObject json = new JsonObject();
            json.put("operator", operator);
            this.vertx.eventBus().publish("observer.new", json.encode());
            rc.response().putHeader("content-type", "application/json").end(
                    new JsonObject().put("selectReceived", true).encode()
            );
        });
    }

    /**
     *
     * @param rc
     */
    private void stopOpenBTS(RoutingContext rc) {
        command.clear();
        command.add("badimsicore_openbts");
        command.add("stop");

        vertx.executeBlocking(future -> {
            launchAndWait(future);
        }, res -> {
            JsonObject answer = new JsonObject();
            answer.put("stopped", res.succeeded());
            if (res.failed()) {
                answer.put("error", res.cause().getMessage());
            }
            rc.response().putHeader("content-type", "application/json").end(
                    answer.encode()
            );
        });
    }

    /**
     *
     * @param rc
     */
    private void sendSMS(RoutingContext rc) {
        if (timsiThreadManager != null) {
            timsiThreadManager.stop();
        }
        rc.request().bodyHandler(h -> {
            JsonObject reqJson = formatJsonParams(h);

            String msisdnSender = reqJson.getString("sender");
            String msg = reqJson.getString("message");
            String imsi = reqJson.getString("imsi");

            command.clear();
            command.add("badimsicore_sms_sender");
            command.add(imsi);
            command.add(msisdnSender);
            command.add(msg);

            vertx.executeBlocking(future -> {
                launchAndWait(future);
            }, res -> {
                JsonObject answer = new JsonObject();
                answer.put("sended", res.succeeded());
                if (res.succeeded()) {
                    Process p = (Process) res.result();
                    BufferedReader br
                            = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    br.lines().forEach(l -> {
                        // TODO
                        System.out.println(l);
                    });
                } else {
                    answer.put("error", res.cause().getMessage());
                }
                rc.response().putHeader("content-type", "application/json").end(
                        answer.encode()
                );
            });
        });
    }

    /**
     *
     * @param rc
     */
    private void launchSmsReceptor(RoutingContext rc) {
        command.clear();
        command.add("badimsicore_sms_interceptor");
        command.add("-i");
        command.add("scripts/smqueue.txt");

        smsThreadManager = new SynchronousThreadManager(command.stream().toArray(String[]::new), 5000);
        vertx.executeBlocking(future -> {
            smsThreadManager.start((in, out) -> {
                BufferedReader bf
                        = new BufferedReader(new InputStreamReader(in));
                bf.lines().forEach(line -> {
                    String removeParenthesis = line.replaceAll("[()]", "");
                    String[] splitted = removeParenthesis.split(",");
                    String first = splitted[0].replaceAll("['']", "");
                    String second = splitted[1].replaceAll("['']", "");
                    vertx.eventBus().publish("sms.new", new Sms(first, second).toJson());
                });
            });
            future.complete();
        }, res -> {
            // Do nothing
        });
    }

    /**
     *
     * @param rc
     */
    private void launchTimsiReceptor(RoutingContext rc) {
        command.clear();
        command.add("badimsicore_openbts");
        command.add("tmsis");

        timsiThreadManager
                = new SynchronousThreadManager(command.stream().toArray(String[]::new), 5000);
        vertx.executeBlocking(future -> {
            timsiThreadManager.start((in, out) -> {
                // TODO AisukoWasTaken !!
                BufferedReader bf = new BufferedReader(new InputStreamReader(in));
                bf.lines().skip(2).forEach(line -> {
                    String[] words = line.split(" ");
                    MobileTarget target
                            = new MobileTarget(words[0], words[1], words[2], "", "", "", "");
                    vertx.eventBus().publish("imsi.new", target.toJson());
                });
            });
        }, res -> {
            // Do nothing
        });
    }

    /**
     * Blocking code executed by another thread handle by VertX
     *
     * @param future : The future which will be executed
     */
    private void launchAndWait(Future<Object> future) {
        try {
            Process p = pythonManager.run(command.stream().toArray(String[]::new));
            p.waitFor();
            future.complete(p);
        } catch (InterruptedException | IOException ex) {
            BadIMSILogger.getLogger().log(Level.SEVERE, null, ex);
            future.fail(ex);
        }
    }

    /**
     * Extract and format in JSON all Bts classes stored in the operator list
     * during the sniffing step
     *
     * @return a JsonArray containing all Bts as JsonObjects in the following
     * order : Network, MCC, LAC, CI and a list of ARFCN
     */
    private JsonArray extractAndFormatBtsList() {
        JsonArray array = new JsonArray();
        operatorList.forEach(bts -> {
            JsonObject tab = new JsonObject();
            tab.put("Network", bts.getOperatorByMnc());
            tab.put("MCC", bts.getOperator().getMcc());
            tab.put("LAC", bts.getLac());
            tab.put("CI", bts.getCi());
            StringBuilder sb = new StringBuilder();
            bts.getArfcn().forEach(arfcn -> {
                sb.append(arfcn);
                sb.append(", ");
            });
            sb.delete(sb.length() - 2, sb.length());
            tab.put("ARFCNs", sb.toString());
            array.add(tab);
        });
        return array;
    }

    /**
     *
     * @param params
     * @param reqJson
     * @param h
     */
    private void parseJsonParams(Map<String, String> params, Buffer h) {
        String bufferMessage = h.toString();
        String[] paramSplits = bufferMessage.split("&");
        String[] valueSplits;

        for (String param : paramSplits) {
            valueSplits = param.split("=");
            if (valueSplits.length > 1) {
                String msg = valueSplits[1].replace("+", " ");
                params.put((valueSplits[0]), msg);
            }
        }
    }

    /**
     *
     * @param h
     */
    private JsonObject formatJsonParams(Buffer h) {
        JsonObject reqJson = new JsonObject();
        HashMap<String, String> params = new HashMap<>();
        parseJsonParams(params, h);
        params.keySet().stream().forEach((key) -> {
            reqJson.put(key, params.get(key));
        });
        return reqJson;
    }

    /**
     * Signal to the observer the launch of a long server treatment waiting
     * return
     */
    private void signalObserverForStartingLongTreatment() {
        JsonObject json = new JsonObject().put("started", true);
        this.vertx.eventBus().publish("observer.new", json.encode());
    }
}
