package org.awesoma.monitoring.config;

import org.awesoma.monitoring.application.port.CurrentActor;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.port.IncidentPresenter;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorPresenter;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.ProjectPresenter;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.TagPresenter;
import org.awesoma.monitoring.application.port.TagRepositoryPort;
import org.awesoma.monitoring.application.port.UserDirectoryPort;
import org.awesoma.monitoring.application.usecase.IncidentService;
import org.awesoma.monitoring.application.usecase.MonitorCheckService;
import org.awesoma.monitoring.application.usecase.MonitorService;
import org.awesoma.monitoring.application.usecase.PermissionPolicy;
import org.awesoma.monitoring.application.usecase.ProjectService;
import org.awesoma.monitoring.application.usecase.ReportDataService;
import org.awesoma.monitoring.application.usecase.TagService;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Builds the use cases from their ports and wraps them in transactions. */
@Configuration(proxyBeanMethods = false)
public class MonitorUseCases {

    /** Infrastructure role: only such advisors are applied without an AspectJ dependency. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor useCaseTransactionAdvisor(BeanFactory beanFactory) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(new UseCaseTransactions());
        // Without it the interceptor finds no transaction manager and silently runs without a transaction.
        interceptor.setBeanFactory(beanFactory);
        return new DefaultPointcutAdvisor(new ComposablePointcut(UseCaseTransactions::isUseCase), interceptor);
    }

    @Bean
    TagService tagService(TagRepositoryPort tags, TagPresenter presenter) {
        return new TagService(tags, presenter);
    }

    @Bean
    ProjectService projectService(CurrentActor actor, ProjectRepositoryPort projects, MemberRepositoryPort members,
            UserDirectoryPort users, ProjectPresenter presenter, MonitorRepositoryPort monitors, DomainEvents events) {
        return new ProjectService(actor, projects, members, users, presenter, monitors, events);
    }

    @Bean
    MonitorService monitorService(MonitorRepositoryPort monitors, ProjectService projects, TagService tags,
            MonitorPresenter presenter, DomainEvents events) {
        return new MonitorService(monitors, projects, tags, presenter, events);
    }

    @Bean
    IncidentService incidentService(IncidentRepositoryPort incidents, MonitorService monitors,
            IncidentPresenter presenter, DomainEvents events) {
        return new IncidentService(incidents, monitors, presenter, events);
    }

    @Bean
    MonitorCheckService monitorCheckService(MonitorRepositoryPort monitors, IncidentRepositoryPort incidents,
            DomainEvents events) {
        return new MonitorCheckService(monitors, incidents, events);
    }

    @Bean
    PermissionPolicy permissionPolicy(UserDirectoryPort users, ProjectRepositoryPort projects,
            MemberRepositoryPort members, MonitorRepositoryPort monitors, IncidentRepositoryPort incidents) {
        return new PermissionPolicy(users, projects, members, monitors, incidents);
    }

    @Bean
    ReportDataService reportDataService(ProjectRepositoryPort projects, MonitorRepositoryPort monitors,
            IncidentRepositoryPort incidents) {
        return new ReportDataService(projects, monitors, incidents);
    }
}
