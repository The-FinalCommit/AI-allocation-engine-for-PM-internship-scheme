package in.pragati.config;

import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the built frontend (single-origin deployment) with SPA fallback. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = Paths.get(props.getWeb().getStaticDir()).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + dir + "/", "classpath:/static/");
    }
}
