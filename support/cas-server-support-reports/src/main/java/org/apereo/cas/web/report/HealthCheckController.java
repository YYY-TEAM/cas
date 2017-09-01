package org.apereo.cas.web.report;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.monitor.HealthCheckMonitor;
import org.apereo.cas.monitor.HealthStatus;
import org.apereo.cas.monitor.Monitor;
import org.apereo.cas.util.CasVersion;
import org.apereo.cas.util.InetAddressUtils;
import org.apereo.cas.util.serialization.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Reports overall CAS health based on the observations of the configured {@link HealthCheckMonitor} instance.
 *
 * @author Marvin S. Addison
 * @since 3.5
 */
@Controller("healthCheckController")
@RequestMapping("/status")
public class HealthCheckController {

    @Autowired
    private CasConfigurationProperties casProperties;
    
    private Monitor<HealthStatus> healthCheckMonitor;

    /**
     * Handle request.
     *
     * @param request  the request
     * @param response the response
     * @return the model and view
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    protected void handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final HealthStatus healthStatus = healthCheckMonitor.observe();
        response.setStatus(healthStatus.getCode().value());

        if (StringUtils.equals(request.getParameter("format"), "json")) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            JsonUtils.render(healthStatus.getDetails(), response);
        } else {
            final StringBuilder sb = new StringBuilder();
            sb.append("Health: ").append(healthStatus.getCode());

            final AtomicInteger i = new AtomicInteger();
            healthStatus.getDetails().forEach((name, status) -> {
                response.addHeader("X-CAS-" + name, String.format("%s;%s", status.getCode(), status.getDescription()));
                sb.append("\n\n\t").append(i.incrementAndGet()).append('.').append(name).append(": ");
                sb.append(status.getCode());
                if (status.getDescription() != null) {
                    sb.append(" - ").append(status.getDescription());
                }
            });   
            sb.append("\n\nHost:\t\t").append(
                    StringUtils.isBlank(casProperties.getHost().getName())
                            ? InetAddressUtils.getCasServerHostName()
                            : casProperties.getHost().getName()
            );

            sb.append("\nServer:\t\t").append(casProperties.getServer().getName());
            sb.append("\nVersion:\t").append(CasVersion.getVersion());

            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            try (Writer writer = response.getWriter()) {
                IOUtils.copy(new ByteArrayInputStream(sb.toString().getBytes(response.getCharacterEncoding())),
                        writer,
                        StandardCharsets.UTF_8);
                writer.flush();
            }
        }
    }

    public void setHealthCheckMonitor(final Monitor<HealthStatus> healthCheckMonitor) {
        this.healthCheckMonitor = healthCheckMonitor;
    }
}
