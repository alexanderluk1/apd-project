package org.example;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CrackTask implements Runnable {
      private final User user;
      private final Map<String, String> hashToPassword;
      private final AtomicInteger passwordsFound;
      private final AtomicInteger processedUsers;

      public CrackTask(User user, Map<String, String> hashToPassword, AtomicInteger passwordsFound,
                  AtomicInteger processedUsers) {
            this.user = user;
            this.hashToPassword = hashToPassword;
            this.passwordsFound = passwordsFound;
            this.processedUsers = processedUsers;
      }

      @Override
      public void run() {
            try {
                  if (user != null && !user.isFound()) {
                        String crackedPassword = hashToPassword.get(user.getHashedPassword());
                        if (crackedPassword != null) {
                              user.setFoundPassword(crackedPassword);
                              passwordsFound.incrementAndGet();
                        }
                  }
            } finally {
                  processedUsers.incrementAndGet();
            }
      }

}
