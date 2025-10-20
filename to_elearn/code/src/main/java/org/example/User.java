package org.example;

public class User {
      private String username;
      private String hashedPassword;
      private boolean isFound = false;
      private String foundPassword = null;

      public User(String username, String hashedPassword) {
            this.username = username;
            this.hashedPassword = hashedPassword;
      }

      public boolean isFound() {
            return isFound;
      }

      public String getHashedPassword() {
            return hashedPassword;
      }

      public void setFoundPassword(String foundPassword) {
            this.foundPassword = foundPassword;
            this.isFound = true;
      }

      public String getUsername() {
            return username;
      }

      public String getFoundPassword() {
            return foundPassword;
      }
}
