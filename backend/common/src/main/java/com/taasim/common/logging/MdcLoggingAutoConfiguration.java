package com.taasim.common.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MdcLoggingAutoConfiguration {

    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration() {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcFilter());
        registration.addUrlPatterns("/*");
        registration.setName("mdcFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
