package org.springframework.security.config.http;

import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.security.config.authentication.AbstractUserDetailsServiceBeanDefinitionParser;
import org.springframework.security.config.authentication.CachingUserDetailsService;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;

/**
 * Bean used to lookup a named UserDetailsService or AuthenticationUserDetailsService.
 *
 * @author Luke Taylor
 * @since 3.1
 */
public class UserDetailsServiceFactoryBean implements ApplicationContextAware {

    private ApplicationContext beanFactory;

    UserDetailsService userDetailsService(String id) {
        if (!StringUtils.hasText(id)) {
            return getUserDetailsService();
        }

        return (UserDetailsService) beanFactory.getBean(id);
    }

    UserDetailsService cachingUserDetailsService(String id) {
        if (!StringUtils.hasText(id)) {
            return getUserDetailsService();
        }
        // Overwrite with the caching version if available
        String cachingId = id + AbstractUserDetailsServiceBeanDefinitionParser.CACHING_SUFFIX;

        if (beanFactory.containsBeanDefinition(cachingId)) {
            return (UserDetailsService) beanFactory.getBean(cachingId);
        }

        return (UserDetailsService) beanFactory.getBean(id);
    }

    @SuppressWarnings("unchecked")
    AuthenticationUserDetailsService authenticationUserDetailsService(String name) {
        UserDetailsService uds;

        if (!StringUtils.hasText(name)) {
            Map<String,?> beans = getBeansOfType(AuthenticationUserDetailsService.class);

            if (!beans.isEmpty()) {
                if (beans.size() > 1) {
                    throw new ApplicationContextException("More than one AuthenticationUserDetailsService registered." +
                            " Please use a specific Id reference.");
                }
                return (AuthenticationUserDetailsService) beans.values().toArray()[0];
            }

            uds = getUserDetailsService();
        } else {
            Object bean = beanFactory.getBean(name);

            if (bean instanceof AuthenticationUserDetailsService) {
                return (AuthenticationUserDetailsService)bean;
            } else if (bean instanceof UserDetailsService) {
                uds = cachingUserDetailsService(name);

                if (uds == null) {
                    uds = (UserDetailsService)bean;
                }
            } else {
                throw new ApplicationContextException("Bean '" + name + "' must be a UserDetailsService or an" +
                        " AuthenticationUserDetailsService");
            }
        }

        return new UserDetailsByNameServiceWrapper(uds);
    }

    /**
     * Obtains a user details service for use in RememberMeServices etc. Will return a caching version
     * if available so should not be used for beans which need to separate the two.
     */
    private UserDetailsService getUserDetailsService() {
        Map<String,?> beans = getBeansOfType(CachingUserDetailsService.class);

        if (beans.size() == 0) {
            beans = getBeansOfType(UserDetailsService.class);
        }

        if (beans.size() == 0) {
            throw new ApplicationContextException("No UserDetailsService registered.");

        } else if (beans.size() > 1) {
            throw new ApplicationContextException("More than one UserDetailsService registered. Please " +
                    "use a specific Id reference in <remember-me/> <openid-login/> or <x509 /> elements.");
        }

        return (UserDetailsService) beans.values().toArray()[0];
    }

    public void setApplicationContext(ApplicationContext beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    private Map<String,?> getBeansOfType(Class<?> type) {
        Map<String,?> beans = beanFactory.getBeansOfType(type);

        // Check ancestor bean factories if they exist and the current one has none of the required type
        BeanFactory parent = beanFactory.getParentBeanFactory();
        while (parent != null && beans.size() == 0) {
            if (parent instanceof ListableBeanFactory) {
                beans = ((ListableBeanFactory)parent).getBeansOfType(type);
            }
            if (parent instanceof HierarchicalBeanFactory) {
                parent = ((HierarchicalBeanFactory)parent).getParentBeanFactory();
            } else {
                break;
            }
        }

        return beans;
    }

}
