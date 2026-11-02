package org.awesoma.report.adapters.out.http;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Called with service credentials; monitor-service owns the projects. */
@FeignClient(name = "monitor-service", contextId = "projectLookupClient")
public interface ProjectLookupClient {

    @GetMapping("/internal/projects/{id}/exists")
    boolean projectExists(@PathVariable("id") Long id);
}
