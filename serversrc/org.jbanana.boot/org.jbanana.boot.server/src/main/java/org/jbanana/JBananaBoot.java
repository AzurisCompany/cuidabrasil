package org.jbanana;

import static org.jbanana.core.Convetions.PKG_KEY_BO;
import static org.jbanana.core.Convetions.PKG_KEY_CONFIG;
import static org.jbanana.core.Convetions.PKG_KEY_REST;
import static org.jbanana.core.Convetions.PKG_KEY_ROOT;
import static org.jbanana.core.Convetions.PKG_VALUE_BO;
import static org.jbanana.core.Convetions.PKG_VALUE_CONFIG;
import static org.jbanana.core.Convetions.PKG_VALUE_REST;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.jbanana.core.Container;
import org.jbanana.core.RootAndSequences;
import org.jbanana.exception.InfraRuntimeException;
import org.jbanana.rest.HandlerInterceptor;
import org.jbanana.rest.ObjectDefinition;
import org.jbanana.rest.RestHandler;
import org.jbanana.rest.RestMap;
import org.jbanana.rest.RestMapper;
import org.jbanana.rest.Restable;
import org.jbanana.xstream.FieldConverter;
import org.jbanana.xstream.RestMapConverter;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.thoughtworks.xstream.XStream;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.impl.StaticHandlerImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sf.saxon.TransformerFactoryImpl;

@Slf4j
public class JBananaBoot {

	@Setter
	private static int port = 8080;
	
	private static Vertx _vertx;
	private static HttpServer _server;
	private static Router _router;
	
	private static HandlerInterceptor[] _interceptor = null;
	
	public static void start(Serializable...prevalentSystems) { start(_interceptor, prevalentSystems);}
	public static void start(HandlerInterceptor interceptor, Serializable...prevalentSystems) {
		start(new HandlerInterceptor[] {interceptor}, prevalentSystems);
	}
	public static void start(HandlerInterceptor[] interceptor, Serializable...prevalentSystems) {
		_interceptor = interceptor;
		_vertx = Vertx.vertx();
		_server = _vertx.createHttpServer();
		_router = Router.router(_vertx);
		_router.route().handler(BodyHandler.create());
		_server.requestHandler(_router::accept);
		_router.route("/static/*").handler(
			new StaticHandlerImpl("static", ClassLoader.getSystemClassLoader()));
		
		for (Serializable ps : prevalentSystems) 
			startContext(ps);
		
		startListen();
	}
	
	private static void startContext(Serializable prevalentSystem) {

		try {
			Container container = new Container(prevalentSystem.getClass());
			start(container, prevalentSystem);

		} catch (Throwable cause) {
			throw new InfraRuntimeException(cause);
		}
	}

	@SuppressWarnings("unchecked")
	public static void start(Container container, Serializable prevalentSystem) throws IOException {

		log.info("starting jbanana.reboot...");

		YamlReader reader = new YamlReader(new FileReader("application.yml"));
		Map<String, ?> yml;
		yml = (Map<String, ?>) reader.read();
		
		for (String key : yml.keySet()) 
			container.registry(key, ""+yml.get(key), true);
		
		String bo =  prevalentSystem.getClass().getPackage().getName();
		String root = bo.substring(0, bo.length() - PKG_VALUE_BO.length());
		String config = root + PKG_VALUE_CONFIG;
		String rest = root + PKG_VALUE_REST;
				
		//		root + PKG_VALUE_BO;

		container.registry(PKG_KEY_ROOT, root, false);
		container.registry(PKG_KEY_CONFIG, config, false);
		container.registry(PKG_KEY_REST, rest, false);
		container.registry(PKG_KEY_BO, bo, false);

		ClassPath cp = ClassPath.from(JBananaBoot.class.getClassLoader());
		
		ImmutableSet<ClassInfo> restClasses = cp.getTopLevelClasses(rest);
		Map<Class<?>, List<RestMap>> result = mapEntryPoints(container, restClasses);
		
		ImmutableSet<ClassInfo> retables = cp.getTopLevelClasses(bo);
		Map<Class<?>, List<RestMap>> rr = mapRestables(container, retables);
		
		for (Entry<Class<?>, List<RestMap>> entry : rr.entrySet()) {
			if(!result.containsKey(entry.getKey()))
				result.put(entry.getKey(), new ArrayList<>());
			
			List<RestMap> list = result.get(entry.getKey());
			list.addAll(entry.getValue());
		}
		
		writeRestXml(container, result);		
		transformXML(new File("./info/" + container.getAlias() + "/_All.xml"), 
								new File("./xsl/swagger.xsl"), 
								new File("./src/main/resources/static/" + container.getAlias() + ".yml"));

		container.initPrevayler(new RootAndSequences(prevalentSystem));
	}

   public static void transformXML(File xmlFile, File xsltFile, File outputFile){

	   	StreamSource xml = new StreamSource(xmlFile);
	   	StreamSource xslt = new StreamSource(xsltFile);
        StreamResult result = new StreamResult(outputFile);
        System.out.println(xmlFile.getName() + " + " + xsltFile.getName() + ": ");
        System.out.println(outputFile.getAbsolutePath());
        
        TransformerFactory transFact = new TransformerFactoryImpl();
        try {
			Transformer trans = transFact.newTransformer(xslt);
			trans.transform(xml, result);
			
		} catch (TransformerException e) {
			e.printStackTrace();
		}
    }

	private static void writeRestXml(Container container , Map<Class<?>, List<RestMap>> result) throws IOException {
		System.out.println();
		File folder = new File("info", container.getAlias());
		if(folder.exists()) FileUtils.forceDelete(folder);
		folder.mkdirs();
		
		List<ObjectDefinition> defs = new ArrayList<>();
		for (Class<?> key : result.keySet()) {
			ObjectDefinition def = new ObjectDefinition(container, key);
			defs.add(def);
		}
		
		List<Property> conventions = new ArrayList<>();
		for (Entry<String, String> entry : container.getConventions().entrySet()) 
			conventions.add(new Property(entry.getKey(), entry.getValue()));
		
		Object out = new Object[]{result, defs, conventions};

		XStream xstream = new XStream();
		xstream.registerConverter(new FieldConverter());
		xstream.registerConverter(new RestMapConverter());
		File file = new File(folder, "_All.xml");
		System.out.println(file.getAbsolutePath());
		FileUtils.write(file, xstream.toXML(out));		
		
		for (Entry<Class<?>, List<RestMap>> entry : result.entrySet()) {
			String name = entry.getKey().getName() + ".xml";
			name = name.replaceAll(container.getConventions().get(PKG_KEY_BO) + ".", "");
			file = new File(folder, name);
			System.out.println(file.getAbsolutePath());
			FileUtils.write(file, xstream.toXML(entry.getValue()));
		}
	}

	@SuppressWarnings("unchecked")
	public static Map<Class<?>, List<RestMap>> mapRestables(Container container, ImmutableSet<ClassInfo> restClasses) {
		
		Map<Class<?>, List<RestMap>> result = new HashMap<>();
		for (ClassInfo info : restClasses) {
			RestMapper mapper = container.my(RestMapper.class);
			try {
				Class<?> clazz = Class.forName(info.getName());
				if(!Restable.class.isAssignableFrom(clazz)) continue;
				
				List<RestMap> methods = mapper.inspectRestableAndMap(container, (Class<? extends Restable>) clazz);
				for (RestMap map : methods)
					mapRestMethod(container, map);

				if(methods != null && methods.size()>0)
					result.put(clazz, methods);
				
			}catch(ClassNotFoundException e) {log.error("Error mapping entry point: " + info.getName(), e); }
		}
		
		return result;
	}

	private static Map<Class<?>, List<RestMap>> mapEntryPoints(Container container, ImmutableSet<ClassInfo> restClasses) {
		
		Map<Class<?>, List<RestMap>> result = new HashMap<>();
		for (ClassInfo info : restClasses) {
			RestMapper mapper = container.my(RestMapper.class);
			try {
				Class<?> clazz = Class.forName(info.getName());
				List<RestMap> methods = mapper.inspectEntryPointAndMap(container, clazz);
				for (RestMap map : methods)
					mapRestMethod(container, map);
				
				if(methods != null && methods.size()>0)
					result.put(clazz, methods);
				
			}catch(ClassNotFoundException e) {log.error("Error mapping entry point: " + info.getName(), e); }
		}
		
		return result;
	}

	protected static void mapRestMethod(Container container, RestMap map) {
		try {
			log.info(map.resume());
			String hm = map.getRest();
			HttpMethod http = HttpMethod.valueOf(hm.toUpperCase());
			String routable = map.getRoutablePath();
			String splitable = map.getSplitablePath();
			Route route = _router.route(http, routable);

			if(!splitable.equals(routable))
				route.pathRegex(splitable);
			
			route.handler(new RestHandler(container, map, _interceptor));
		} catch (Exception e) {log.error(e.getMessage(), e);}
	}

//	private static Class<?> findMainClass() throws ClassNotFoundException {
//		RuntimeException ex = new RuntimeException();
//		ex.fillInStackTrace();
//
//		StackTraceElement[] trace = ex.getStackTrace();
//		for (int i = 0; i < trace.length; i++) {
//			StackTraceElement element = trace[i];
//			if (!element.getMethodName().equals("main")) continue;
//
//			String className = trace[i].getClassName();
//			return Class.forName(className);
//		}
//		throw new ClassNotFoundException();
//	}	

	private static void startListen() {
		_server.listen(port, 
				result -> {
					if (result.succeeded()) {
						log.info("prevayler.boot: " + port + " is started.");
						return;
					}
					System.exit(1);
				});
	}

	public static void stop() {	
		log.info("prevayler.boot is terminated.");
		System.exit(0);
	}
}

@Data
@AllArgsConstructor
class Property{
	private String key;
	private String value;
}