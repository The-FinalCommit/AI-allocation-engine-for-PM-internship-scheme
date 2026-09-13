package in.pragati.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA route fallback: any GET path that is not an API, asset, swagger or
 * actuator path forwards to index.html so client-side deep links work.
 * Paths containing a dot (files) are excluded so static assets resolve.
 */
@Controller
public class SpaFallbackController {

    @RequestMapping(value = "/{p:(?!api$|swagger-ui$|v3$|actuator$|data$|assets$)[^\\.]*}/**")
    public String deepRoute() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/{p:(?!api$|swagger-ui$|v3$|actuator$|data$|assets$)[^\\.]*}")
    public String topRoute() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/")
    public String root() {
        return "forward:/index.html";
    }
}
