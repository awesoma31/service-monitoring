package org.awesoma.security;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "monitor-service", contextId = "resourceAccessClient")
public interface ResourceAccessClient {
    @GetMapping("/internal/access/{resource}/{id}")
    boolean allowed(@PathVariable("resource") String resource, @PathVariable("id") Long id,
            @RequestParam("user_id") Long userId, @RequestParam("write") boolean write);
}
