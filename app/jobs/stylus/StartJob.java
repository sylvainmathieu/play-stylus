package jobs.stylus;

import play.Logger;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.libs.Codec;

@OnApplicationStart
public class StartJob extends Job<Void> {

	public static String id;

	@Override
	public void doJob() {
		this.id = Codec.UUID().substring(0, 6);
	}

}
