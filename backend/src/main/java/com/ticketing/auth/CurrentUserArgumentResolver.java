package com.ticketing.auth;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.ticketing.shared.security.CurrentUser;
import com.ticketing.shared.security.Role;

/** Injects a CurrentUser for any handler parameter of that type, built from the security context. */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails details) {
            Set<Role> roles = details.getAuthorities().stream()
                    .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                    .map(Role::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
            return new CurrentUser(details.userId(), details.getUsername(), roles, details.emailVerified());
        }
        return null;
    }
}
