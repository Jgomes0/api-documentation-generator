package fixtures.library.controller;

import fixtures.library.dto.BookDto;
import fixtures.library.dto.CreateBookRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping
    public List<BookDto> list(@RequestParam(required = false) String author) {
        return null;
    }

    @GetMapping("/{id}")
    public BookDto findById(@PathVariable Long id) {
        return null;
    }

    @PostMapping
    public BookDto create(@RequestBody CreateBookRequest request) {
        return null;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
    }
}