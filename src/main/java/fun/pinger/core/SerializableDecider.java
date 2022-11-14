package fun.pinger.core;

import fun.pinger.model.ClassReference;
import fun.pinger.source.javaserial.JavaSerializableDecider;

import java.util.function.Function;

/**
 * Represents logic to decide if a class is serializable. The simple case (implemented by
 * {@link JavaSerializableDecider}) just checks if the class implements serializable. Other use-cases may have more
 * complicated logic.
 */
public interface SerializableDecider extends Function<ClassReference.Handle, Boolean> {
}
