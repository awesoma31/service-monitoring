package org.awesoma.user.application.model;
import java.util.List;
import org.awesoma.user.domain.User;
public record UserPage(List<User> items, long total, int page, int size) {}
