package dev.progames723.mc_threading.interface_injects;

import org.jetbrains.annotations.Nullable;

public interface InjectionAndMixinIntegrityTest {
	@Nullable
	default Boolean isGood() {
		throw new AssertionError("Not transformed!");
	}
}
