package org.awesoma.user.application.port;
import org.awesoma.user.application.model.Token;
public interface TokenIssuer { Token issue(String subject, String role); }
