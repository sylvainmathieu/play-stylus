package play.modules.stylus;

import play.Play;
import play.PlayPlugin;

public class StylusPlugin extends PlayPlugin {

    @Override
    public void onLoad() {
        if (!Play.usePrecompiled && (Play.mode == Play.Mode.PROD || StylusCompiler.precompiling)) {
        	StylusCompiler.compileAll();
        }
    } 

}
