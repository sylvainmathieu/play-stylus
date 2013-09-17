package controllers.stylus;

import play.Play;
import play.modules.stylus.StylusCompiler;
import play.mvc.Controller;

public class StylusController extends Controller {

	public static void style(String startId, String styleName) {
		String compiledStyle = StylusCompiler.compileStyle(styleName);
		response.setHeader("Content-Length", Integer.toString(compiledStyle.length()));
		response.contentType = "text/css";
		if (Play.mode.isProd()) {
			response.cacheFor("1h");
		}
		renderText(compiledStyle);
	}

}