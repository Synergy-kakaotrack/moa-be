package com.moa.moa_backend.global.config;

import com.moa.moa_backend.domain.user.repository.UserRepository;
import com.moa.moa_backend.global.filter.UserIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<UserIdFilter> userIdfilter(UserRepository userRepository){
        FilterRegistrationBean<UserIdFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new UserIdFilter(userRepository));

        filterRegistrationBean.addUrlPatterns("/api/*"); //API 요청에만 적용한다.
        filterRegistrationBean.setOrder(1); //다른 필터보다 먼저

        return filterRegistrationBean;
    }

}
