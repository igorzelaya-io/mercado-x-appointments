package hn.alturaforge.mercadox.appointments.google.oauth;

import hn.alturaforge.mercadox.library.entity.model.appointments.GoogleOAuthAuthorizationTransaction;

import java.time.Duration;
import java.util.Optional;

public interface GoogleOAuthStateStore {

    boolean save(String state,
                 GoogleOAuthAuthorizationTransaction transaction,
                 Duration ttl);

    Optional<GoogleOAuthAuthorizationTransaction> consume(String state);
}
