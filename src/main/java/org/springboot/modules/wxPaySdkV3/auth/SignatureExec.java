package org.springboot.modules.wxPaySdkV3.auth;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.execchain.ClientExecChain;

import java.io.IOException;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpStatus.SC_MULTIPLE_CHOICES;
import static org.apache.http.HttpStatus.SC_OK;

/**
 * @author xy-peng
 */
public class SignatureExec implements ClientExecChain {

    private static final String WECHAT_PAY_HOST_NAME_SUFFIX = ".mch.weixin.qq.com";
    private final ClientExecChain mainExec;
    private final Credentials credentials;
    private final Validator validator;

    protected SignatureExec(Credentials credentials, Validator validator, ClientExecChain mainExec) {
        this.credentials = credentials;
        this.validator = validator;
        this.mainExec = mainExec;
    }

    protected void convertToRepeatableResponseEntity(CloseableHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            response.setEntity(new BufferedHttpEntity(entity));
        }
    }

    protected void convertToRepeatableRequestEntity(HttpRequestWrapper request) throws IOException {
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity != null) {
                ((HttpEntityEnclosingRequest) request).setEntity(new BufferedHttpEntity(entity));
            }
        }
    }

    @Override
    public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request, HttpClientContext context,
            HttpExecutionAware execAware) throws IOException, HttpException {
        if (request.getTarget().getHostName().endsWith(WECHAT_PAY_HOST_NAME_SUFFIX)) {
            return executeWithSignature(route, request, context, execAware);
        } else {
            return mainExec.execute(route, request, context, execAware);
        }
    }

    private CloseableHttpResponse executeWithSignature(HttpRoute route, HttpRequestWrapper request,
            HttpClientContext context,
            HttpExecutionAware execAware) throws IOException, HttpException {
        // 上传类不需要消耗两次故不做转换
        if (!(request.getOriginal() instanceof WechatPayUploadHttpPost)) {
            convertToRepeatableRequestEntity(request);
        }
        // 添加认证信息
        request.addHeader(AUTHORIZATION, credentials.getSchema() + " " + credentials.getToken(request));

        // 执行
        CloseableHttpResponse response = mainExec.execute(route, request, context, execAware);

        // 对成功应答验签
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() >= SC_OK && statusLine.getStatusCode() < SC_MULTIPLE_CHOICES) {
            convertToRepeatableResponseEntity(response);
            if (!validator.validate(response)) {
                throw new HttpException("应答的微信支付签名验证失败");
            }
        }
        return response;
    }
}
