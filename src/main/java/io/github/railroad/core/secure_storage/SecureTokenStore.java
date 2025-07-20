package io.github.railroad.core.secure_storage;

import com.github.javakeyring.Keyring;

/**
 * SecureTokenStore is a utility class for securely storing and retrieving tokens
 * associated with a specific service name. It uses the Java Keyring library to
 * handle secure storage operations.
 */
public class SecureTokenStore {
    private final String serviceName;

    /**
     * Constructs a SecureTokenStore with the specified service name.
     *
     * @param serviceName the name of the service for which tokens will be stored
     * @throws IllegalArgumentException if the service name is null or empty
     */
    public SecureTokenStore(String serviceName) {
        if (serviceName == null || serviceName.isEmpty())
            throw new IllegalArgumentException("Service name cannot be null or empty");

        this.serviceName = serviceName;
    }

    /**
     * Saves a token associated with the specified account name.
     *
     * @param token       the token to save
     * @param accountName the name of the account associated with the token
     * @throws IllegalArgumentException if the token or account name is null or empty
     * @throws RuntimeException         if there is an error saving the token
     */
    public void saveToken(String token, String accountName) {
        if (token == null || token.isEmpty())
            throw new IllegalArgumentException("Token cannot be null or empty");

        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(this.serviceName, accountName, token);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to save token to secure storage", exception);
        }
    }

    /**
     * Retrieves a token associated with the specified account name.
     *
     * @param accountName the name of the account for which to retrieve the token
     * @return the token associated with the account name
     * @throws IllegalArgumentException if the account name is null or empty
     * @throws RuntimeException         if there is an error retrieving the token
     */
    public String getToken(String accountName) {
        if (accountName == null || accountName.isEmpty())
            throw new IllegalArgumentException("Account name cannot be null or empty");

        try (Keyring keyring = Keyring.create()) {
            return keyring.getPassword(this.serviceName, accountName);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to retrieve token from secure storage", exception);
        }
    }

    /**
     * Clears the token associated with the specified account name.
     *
     * @param accountName the name of the account for which to clear the token
     * @throws IllegalArgumentException if the account name is null or empty
     * @throws RuntimeException         if there is an error clearing the token
     */
    public void clearToken(String accountName) {
        if (accountName == null || accountName.isEmpty())
            throw new IllegalArgumentException("Account name cannot be null or empty");

        try (Keyring keyring = Keyring.create()) {
            keyring.deletePassword(this.serviceName, accountName);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to clear token from secure storage", exception);
        }
    }
}
