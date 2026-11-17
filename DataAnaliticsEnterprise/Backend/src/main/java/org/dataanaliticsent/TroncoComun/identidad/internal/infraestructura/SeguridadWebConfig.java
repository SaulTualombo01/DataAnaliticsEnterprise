package org.dataanaliticsent.TroncoComun.identidad.internal.infraestructura;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.DispatcherType;
import org.dataanaliticsent.TroncoComun.errores.ProblemasHttp;
import org.dataanaliticsent.TroncoComun.identidad.internal.dominio.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
@EnableConfigurationProperties({ParametrosIdentidad.class,ParametrosCuentas.class})
public class SeguridadWebConfig {
    @Bean PoliticaInvitaciones politicaInvitaciones(ParametrosCuentas p) { return new PoliticaInvitaciones(p.invitationTtl(),p.manualDeliveryEnabled()); }
    @Bean Clock relojIdentidad() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt",Map.of("bcrypt",new BCryptPasswordEncoder(12)));
    }
    @Bean HashClaves hashClaves(PasswordEncoder encoder) {
        return new HashClaves() {
            public String codificar(String clave) { return encoder.encode(clave); }
            public boolean coincide(String clave,String hash) { return encoder.matches(clave,hash); }
        };
    }
    @Bean UserDetailsService usuariosLocales(RepositorioIdentidad datos,Clock reloj) {
        return email -> new PrincipalLocal(datos.porCorreo(FiltroLimiteLogin.normalizar(email))
                .orElseThrow(()->new UsernameNotFoundException("Credenciales inválidas")),reloj.instant());
    }
    @Bean DaoAuthenticationProvider proveedorAutenticacion(UserDetailsService usuarios,PasswordEncoder encoder) {
        var provider=new DaoAuthenticationProvider(usuarios); provider.setPasswordEncoder(encoder); return provider;
    }
    @Bean LimitadorIntentos limitadorIntentos(Clock reloj,ParametrosIdentidad parametros) {
        return new LimitadorIntentos(reloj,parametros.loginWindow(),parametros.loginAccountLimit(),parametros.loginIpLimit(),10000);
    }
    @Bean SecurityFilterChain seguridad(HttpSecurity http,DaoAuthenticationProvider provider,RepositorioIdentidad datos,
            ProblemasHttp problemas,AuditoriaIdentidad auditoria,LimitadorIntentos limite,Clock reloj,ParametrosIdentidad parametros,ParametrosCuentas cuentas) throws Exception {
        http.authenticationProvider(provider)
            .requestCache(cache->cache.disable())
            .httpBasic(basic->basic.disable())
            .authorizeHttpRequests(auth->auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/invitations/accept").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/admin/users","/api/v1/organizations/*/users","/api/v1/organizations/*/invitations").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/organizations/*/invitations","/api/v1/organizations/*/invitations/*/revocations",
                    "/api/v1/admin/users/*/deactivations","/api/v1/organizations/*/users/*/deactivations").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/admin/users/*","/api/v1/organizations/*/users/*",
                    "/api/v1/organizations/*/memberships","/api/v1/organizations/*/functional-roles",
                    "/api/v1/organizations/*/projects/*","/api/v1/organizations/*/projects/*/memberships",
                    "/api/v1/organizations/*/projects/*/memberships/*/roles","/api/v1/organizations/*/projects/*/memberships/*/role",
                    "/api/v1/auth/contexts","/api/v1/auth/context","/api/v1/auth/contexts/*/permissions").authenticated()
                .requestMatchers(HttpMethod.PATCH,"/api/v1/admin/users/*","/api/v1/organizations/*/users/*","/api/v1/organizations/*/projects/*").authenticated()
                .requestMatchers(HttpMethod.PUT,"/api/v1/organizations/*/projects/*/memberships/*/role").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/organizations/*/memberships/*/revocations","/api/v1/organizations/*/memberships/*/reactivations",
                    "/api/v1/organizations/*/projects/*/archivals","/api/v1/organizations/*/projects/*/reactivations",
                    "/api/v1/organizations/*/projects/*/memberships","/api/v1/organizations/*/projects/*/memberships/*/revocations",
                    "/api/v1/organizations/*/projects/*/memberships/*/reactivations",
                    "/api/v1/organizations/*/projects/*/memberships/*/roles/*/revocations","/api/v1/auth/context").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/auth/me","/api/v1/auth/administration").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/admin/organizations","/api/v1/admin/organizations/*/administrators",
                        "/api/v1/organizations/*","/api/v1/organizations/*/projects").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/admin/organizations","/api/v1/admin/organizations/*/administrators",
                        "/api/v1/admin/organizations/*/administrators/*/revocations","/api/v1/organizations/*/projects").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/password","/api/v1/admin/users/*/sessions/revoke").authenticated()
                // M3 Gobierno: rutas de docs/contratos/m3/gobierno-http.openapi.json; permisos m3.* en cada caso de uso.
                .requestMatchers("/api/v1/projects/*/governance-policies","/api/v1/projects/*/governance-policies/**",
                    "/api/v1/projects/*/assets/*/governance-policies","/api/v1/projects/*/assets/*/governance-profile",
                    "/api/v1/projects/*/assets/*/compliance-evaluations","/api/v1/projects/*/assets/*/compliance",
                    "/api/v1/projects/*/lineage-relations","/api/v1/projects/*/lineage-relations/**",
                    "/api/v1/projects/*/assets/*/lineage","/api/v1/projects/*/audit-events","/api/v1/projects/*/versions/**",
                    "/api/v1/projects/*/assets/*/governance-summary","/api/v1/projects/*/assets/*/governance-history").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((req,res,error)->problemas.escribir(req,res,401,"AUTHENTICATION_REQUIRED","Inicia sesión para continuar"))
                .accessDeniedHandler((req,res,error)->problemas.escribir(req,res,403,
                    error instanceof CsrfException?"CSRF_INVALID":"ACCESS_DENIED","La solicitud no está autorizada")))
            .csrf(csrf->csrf.ignoringRequestMatchers(req->{
                var a=SecurityContextHolder.getContext().getAuthentication();
                return "POST".equals(req.getMethod()) && "/api/v1/auth/logout".equals(req.getServletPath())
                    && (a==null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated());
            }))
            .sessionManagement(session->session.sessionFixation(fixation->fixation.changeSessionId()))
            .formLogin(form->form.loginPage("/api/v1/auth/login").loginProcessingUrl("/api/v1/auth/login").usernameParameter("email").passwordParameter("password")
                .successHandler((req,res,auth)->{
                    var p=(PrincipalLocal)auth.getPrincipal();
                    try {
                        auditoria.registrar(p.usuarioId(),p.usuarioId(),"auth.login","SUCCESS",UUID.fromString(ProblemasHttp.requestId(req)));
                        limite.exito(FiltroLimiteLogin.hash(FiltroLimiteLogin.normalizar(req.getParameter("email"))));
                        problemas.datos(req,res,Map.of("authenticated",true,"user_id",p.usuarioId()));
                    } catch(DataAccessException unavailable) {
                        new SecurityContextLogoutHandler().logout(req,res,auth);
                        problemas.escribir(req,res,503,"IDENTITY_UNAVAILABLE","No se pudo completar el inicio de sesión");
                    }
                })
                .failureHandler((req,res,error)->{
                    try {
                        auditoria.registrar(null,null,"auth.login","FAILURE",UUID.fromString(ProblemasHttp.requestId(req)));
                        if(error instanceof InternalAuthenticationServiceException) problemas.escribir(req,res,503,"IDENTITY_UNAVAILABLE","No se pudo verificar el acceso");
                        else problemas.escribir(req,res,401,"INVALID_CREDENTIALS","Credenciales inválidas");
                    } catch(DataAccessException unavailable) { problemas.escribir(req,res,503,"IDENTITY_UNAVAILABLE","No se pudo verificar el acceso"); }
                }))
            .logout(logout->logout.logoutUrl("/api/v1/auth/logout").deleteCookies("TICSESSION").invalidateHttpSession(true).clearAuthentication(true)
                .logoutSuccessHandler((req,res,auth)->{
                    if(auth!=null && auth.getPrincipal() instanceof PrincipalLocal p) {
                        try { auditoria.registrar(p.usuarioId(),p.usuarioId(),"auth.logout","SUCCESS",UUID.fromString(ProblemasHttp.requestId(req))); }
                        catch(DataAccessException unavailable) { LoggerFactory.getLogger(SeguridadWebConfig.class).warn("No se pudo auditar el cierre; request_id={}",ProblemasHttp.requestId(req)); }
                    }
                    res.setStatus(204);
                }));
        http.addFilterAfter(new FiltroVigenciaSesion(datos,problemas,reloj,parametros.absoluteSessionTimeout()),SecurityContextHolderFilter.class);
        http.addFilterBefore(new FiltroActivacion(reloj,cuentas,problemas),UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(new FiltroLimiteLogin(limite,problemas,auditoria,parametros.loginWindow()),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
