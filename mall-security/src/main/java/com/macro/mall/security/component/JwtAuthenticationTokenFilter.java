package com.macro.mall.security.component;

import com.macro.mall.security.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT登录授权过滤器
 * Created by macro on 2018/4/26.
 */
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);
    /**
     * 自动续期后回传新token的响应头名称
     * 需在 GlobalCorsConfig 中通过 addExposedHeader 暴露，前端才能跨域读取
     */
    private static final String NEW_TOKEN_HEADER = "newToken";
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader(this.tokenHeader);
        if (authHeader != null && authHeader.startsWith(this.tokenHead)) {
            String authToken = authHeader.substring(this.tokenHead.length());// The part after "Bearer "
            String username = jwtTokenUtil.getUserNameFromToken(authToken);
            LOGGER.info("checking username:{}", username);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                if (jwtTokenUtil.validateToken(authToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    LOGGER.info("authenticated user:{}", username);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    // 认证通过后自动续期，刷新token过期时间并回传新token
                    refreshTokenIfNeeded(authHeader, authToken, response);
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 认证通过后自动刷新token过期时间：
     * 只有生成了新的token才会通过响应头 newToken 回传给前端，
     * 前端读取该响应头并更新本地存储的token即可，无需主动调用刷新接口。
     * refreshHeadToken 自带30分钟防抖，30分钟内刚刷新过会返回原token，此时不重复下发。
     *
     * @param authHeader 原始请求头（带tokenHead前缀，refreshHeadToken 需要该格式）
     * @param authToken  去除tokenHead前缀后的token，用于判断是否真的生成了新token
     * @param response   响应，用于回传新token
     */
    private void refreshTokenIfNeeded(String authHeader, String authToken, HttpServletResponse response) {
        String refreshedToken = jwtTokenUtil.refreshHeadToken(authHeader);
        // 过期时 refreshHeadToken 返回null，30分钟内刷过则返回原token，这两种情况都不下发
        if (refreshedToken != null && !refreshedToken.equals(authToken)) {
            response.setHeader(NEW_TOKEN_HEADER, tokenHead + refreshedToken);
        }
    }
}
