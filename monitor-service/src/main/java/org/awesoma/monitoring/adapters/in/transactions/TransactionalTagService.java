package org.awesoma.monitoring.adapters.in.transactions;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.application.port.TagRepositoryPort;
import org.awesoma.monitoring.application.port.TagPresenter;
import org.awesoma.monitoring.application.port.command.TagCreateRequest;
import org.awesoma.monitoring.application.model.view.TagResponse;
import org.awesoma.monitoring.application.usecase.TagService;
import java.util.Set;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.domain.entity.Tag;
@Component
@Transactional(readOnly = true)
public class TransactionalTagService extends TagService {
    public TransactionalTagService(TagRepositoryPort tags,TagPresenter mapper) {
        super(tags,mapper);
    }
    @Override
    @Transactional
    public TagResponse create(TagCreateRequest request) {
        return super.create(request);
    }
    @Override
    @Transactional
    public void delete(Long id) {
        super.delete(id);
    }
    @Override
    @Transactional
    public Set<Tag> resolveOrCreate(Set<String> names) {
        return super.resolveOrCreate(names);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<TagResponse> list(PageRequest page) {
        return super.list(page);
    }
}
