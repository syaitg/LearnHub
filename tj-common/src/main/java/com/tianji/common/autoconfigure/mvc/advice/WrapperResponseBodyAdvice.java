package com.tianji.common.autoconfigure.mvc.advice;

import com.tianji.common.annotations.NoWrapper;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.R;
import com.tianji.common.utils.WebUtils;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@ConditionalOnClass({ResponseBodyAdvice.class})
public class WrapperResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter methodParameter, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        if (methodParameter.hasMethodAnnotation(NoWrapper.class)) {
// 如果方法上标注了 NoWrapper 注解，则不进行包装
            return false;
        }
        Class<?> returnType = methodParameter.getParameterType();
        if (ResponseEntity.class.isAssignableFrom(returnType)
                || Resource.class.isAssignableFrom(returnType)) {
// 文件流、图片等资源响应必须保持原始响应，不能包装成统一 JSON 结构
            return false;
        }
        return returnType != R.class && WebUtils.isGatewayRequest();
    }

    @Override
    public Object beforeBodyWrite(
            Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {
        if (request.getURI().getPath().equals("/v2/api-docs")){
            return body;
        }
        if (body == null) {
            return R.ok().requestId(MDC.get(Constant.REQUEST_ID_HEADER));
        }
        if(body instanceof R){
            return body;
        }
        return R.ok(body).requestId(MDC.get(Constant.REQUEST_ID_HEADER));
    }
}
