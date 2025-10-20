package org.example;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CrackTask implements Runnable {
      private final User user;
      private final Map<String, String> hashToPassword;
      private final AtomicInteger passwordsFound;

      public CrackTask(User user, Map<String, String> hashToPassword, AtomicInteger passwordsFound) {
            this.user = user;
            this.hashToPassword = hashToPassword;
            this.passwordsFound = passwordsFound;
      }

      @Override
      public void run() {
            if (user == null || user.isFound())
                  return;
            String crackedPassword = hashToPassword.get(user.getHashedPassword());
            if (crackedPassword != null) {
                  user.setFoundPassword(crackedPassword);
                  passwordsFound.incrementAndGet();
            }
      }

}
