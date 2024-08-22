package de.tu_dresden.lat.tools;

import java.util.*;

/**
 * @author Christian Alrabbaa
 *
 */
public class MapTools {

	public static <K, V> void update(K key, V value, Map<K, Set<V>> map) {
		if (map.containsKey(key))
			map.get(key).add(value);
		else
			map.put(key, new HashSet<>(Collections.singletonList(value)));
	}
}
