/**
 * Created on Jan 16, 2007 9:28:25 AM
 * 
 * Copyright FullSIX
 */
package hudson.plugins.im;

import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.Publisher;

/**
 * @author bruyeron
 * @version $Id: IMPublisher.java 2678 2007-03-26 09:48:04Z bruyeron $
 */
public abstract class IMPublisher<T extends IMPublisher<T>> extends Publisher {

	/**
	 * @see hudson.tasks.BuildStep#perform(hudson.model.Build, hudson.Launcher, hudson.model.BuildListener)
	 */
	public final boolean perform(Build build, Launcher launcher,
			BuildListener listener) {
		if(build.getPreviousBuild() != null){
			// only broadcast change of status
			if(!build.getResult().toString().equals(build.getPreviousBuild().getResult().toString())){
				publish(build);
			}
		} else {
			// if first build, only broadcast failure/unstable
			if(build.getResult() != Result.SUCCESS){
				publish(build);
                        }
		}
		return true;
	}
	
	
	private final void publish(Build build){
		if(build.getResult() ==  Result.SUCCESS){
			reportSuccess(build);
		} else if(build.getResult() == Result.FAILURE){
			reportFailure(build);
		} else if(build.getResult() == Result.UNSTABLE){
			reportUnstability(build);
		}
	}
	
	protected abstract void reportSuccess(Build build);
	protected abstract void reportFailure(Build build);
	protected abstract void reportUnstability(Build build);

}
