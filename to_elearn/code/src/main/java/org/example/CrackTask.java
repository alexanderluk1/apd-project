package org.example;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CrackTask implements Runnable {
      private User user;
      private Map<String, String> hashToPassword;
      private AtomicInteger passwordsFound;
      private AtomicInteger processedUsers;

      public void setup(User user, Map<String, String> hashToPassword, AtomicInteger passwordsFound,
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
