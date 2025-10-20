package org.example;

import java.io.IOException;

public interface Loader<T> {
      T load(String path) throws IOException;
}
