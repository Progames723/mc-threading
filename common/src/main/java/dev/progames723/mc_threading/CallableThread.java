package dev.progames723.mc_threading;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

import static dev.progames723.mc_threading.MinecraftThreading.LOGGER;

public class CallableThread<T> extends Thread implements Callable<T> {
	private Callable<T> target;
	
	@Override
	public T call() throws Exception {
		return target.call();
	}
	
	@Nullable
	public T startCall() {
		try {
			return call();
		} catch (Exception e) {
			LOGGER.error("Unable to compute result!", e);
			return null;
		}
	}
	
	public CallableThread(Callable<T> target, String name) {
		super(name);
		this.target = target;
	}
	
	public CallableThread(Callable<T> target) {
		super();
		this.target = target;
	}
	
	public CallableThread(String name) {
		super(name);
	}
}
