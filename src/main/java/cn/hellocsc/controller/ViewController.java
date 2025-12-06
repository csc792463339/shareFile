package cn.hellocsc.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    @GetMapping("/view.html")
    public ResponseEntity<Resource> viewShare() {
        return getViewHtml();
    }

    @GetMapping("/view/{shareId}")
    public ResponseEntity<Resource> viewShare(@PathVariable String shareId) {
        return getViewHtml();
    }

    private ResponseEntity<Resource> getViewHtml() {
        try {
            Resource resource = new ClassPathResource("static/view.html");
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("text/html"))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

