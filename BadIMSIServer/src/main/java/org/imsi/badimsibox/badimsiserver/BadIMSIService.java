package org.imsi.badimsibox.badimsiserver;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class BadIMSIService extends AbstractVerticle {
	
	static String defaultHeaders = "Origin, X-Requested-With, Content-Type, Accept";
    static String defaultMethods = "GET, POST, OPTIONS, PUT, HEAD, DELETE, CONNECT";
    static String defaultIpAndPorts = "*"; 
	
	@Override
	public void start() {
		Router router = Router.router(vertx);
		
		router.route().handler(rc -> {
            rc.response().putHeader("Access-Control-Allow-Headers", defaultHeaders);
            rc.response().putHeader("Access-Control-Allow-Methods", defaultMethods);
            rc.response().putHeader("Access-Control-Allow-Origin", defaultIpAndPorts);
            rc.next();
         });
		
		router.get("/master/session/:state").handler(rc -> {
			String name = rc.request().getParam("state");
    		if(name.equals("start")) {
    			/* Code to start session */
    		}
    		
    		if(name.equals("stop")) {
    			/* Code to stop session */
    		}
    		
    		// We have to give the right response
    		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("state", name)
            	.put("key", "value") // in the js, object.key -> value
            	.encode());
    	});  	
    	
		
		router.get("/master/map/:locate").handler(rc -> {	
    		// Actually, this is an example: Remove me when you put the right code
			String name = "48.839915,2.5842899,17z";
    		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("location", name)
            	.encode());
		
			/*
			// Example to call python files from Java
			String[] pythonLocationScript = {"/path/to/script/python/locate_me.py","-i","input","-o", "output"};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("location", pc.getResultSb())
            	.encode());
    		}	
    		*/
    	});
    	
		// TODO : A finir cette tache
    	router.post("/master/sniffing/start/").handler(rc -> {
    		final JsonObject reqJson = new JsonObject();    		
    		rc.request().bodyHandler(h -> {
    			
    		});
    		
    		reqJson.put("state", "start");
    		
       		rc.response()
        	.putHeader("content-type", "application/json")
        	.end(reqJson.encode());

    			/*
    			String data = rc.request().getFormAttribute("operator");
    			System.out.println(data);
    			System.out.println("Haha");
           		*/

    			// get BTS Objects here
    			/* .... */
    			
    			/* Mock datas */
    			/*
    			List<Bts> bouyguesList = new ArrayList<>();
    			
    			List<String> arfcns = new ArrayList<>();
    			arfcns.add("702");
    			arfcns.add("724");
    			arfcns.add("751");
    			
    			List<String> arfcns2 = new ArrayList<>();
    			arfcns2.add("980");
    			arfcns2.add("998");
    			arfcns2.add("1023");
    			
    			bouyguesList.add(new Bts("20", "208", "54", "56254", arfcns));
    			bouyguesList.add(new Bts("20", "208", "54", "56243", arfcns2));
    			bouyguesList.add(new Bts("20", "208", "56", "56265", arfcns));
    			bouyguesList.add(new Bts("20", "208", "56", "56212", arfcns2));
    			*/
    		
    		
    		/*
    		String[] pythonLocationScript = {PythonCaller.getContextPath()+"badimsicore","-l","start"};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("sniffing", pc.getResultSb())
            	.encode());
    		}	
    		*/

    	});
    	
       	router.get("/master/sniffing/stop").handler(rc -> {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("state", "stop")
            	.encode());
    		
    		/*
    		String[] pythonLocationScript = {PythonCaller.getContextPath()+"badimsicore","-l","stop"};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("sniffing", pc.getResultSb())
            	.encode());
    		}	
    		*/

    	});
    	
    	router.post("/master/jamming/start/").handler(rc -> {
    		String operator = rc.request().getParam("operator");
    		if(operator != null) {
        		// We have to give the right response
        		rc.response()
                	.putHeader("content-type", "application/json")
                	.end(new JsonObject().put("state", "start").put("operator", operator)
                	.encode());    			
    		}
    		
    		/*
    		String[] pythonLocationScript = {PythonCaller.getContextPath()+"badimsicore","-j",name};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("jamming", pc.getResultSb())
            	.encode());
    		}	
    		*/
    	});

    	router.get("/master/jamming/stop").handler(rc -> {
    		// We have to give the right response
    		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("state", "stop")
            	.encode());
    		
    		/*
    		String[] pythonLocationScript = {PythonCaller.getContextPath()+"badimsicore","-j",name};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("jamming", pc.getResultSb())
            	.encode());
    		}	
    		*/
    	});

    	router.post("/master/fakebts/start/:bts").handler(rc -> {    		
    		String bts = rc.request().getParam("bts");
    		if(bts != null) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("state", "start").put("BTS", bts)
            	.encode());	
    		}
    		/*
    		String[] pythonLocationScript = {PythonCaller.getContextPath()+"badimsicore","-b","start"};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("fake bts", pc.getResultSb())
            	.encode());
    		}	
    		*/
    	});

    	
    	router.get("/master/fakebts/stop").handler(rc -> {    		
    		// We have to give the right response
    		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("state", "stop")
            	.encode());
    		
    		/*
    		String[] pythonLocationScript = {PythonCaller.getContextPath()+"badimsicore","-b","stop"};
    		PythonCaller pc = new PythonCaller(pythonLocationScript);
    		int exitValue = -1;
    		try {
				exitValue = pc.process();
			} catch (Exception e) {
				e.printStackTrace();
			}
    		
    		if(exitValue == 0) {
        		rc.response()
            	.putHeader("content-type", "application/json")
            	.end(new JsonObject().put("fake bts", pc.getResultSb())
            	.encode());
    		}	
    		*/
    	});

		
		router.route().handler(StaticHandler.create());
		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}
}
