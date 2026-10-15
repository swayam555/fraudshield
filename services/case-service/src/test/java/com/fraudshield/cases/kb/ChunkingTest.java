package com.fraudshield.cases.kb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingTest {

    @Test
    void parsesFrontMatterAndSplitsOnSections() {
        String md = """
                ---
                title: Test Doc
                source: unit test
                ---

                ## First
                Alpha paragraph.

                ## Second
                Beta paragraph.
                """;
        var doc = KnowledgeBaseIngestor.parse(md);
        assertThat(doc.title()).isEqualTo("Test Doc");
        assertThat(doc.source()).isEqualTo("unit test");
        List<KnowledgeBaseIngestor.Chunk> chunks = KnowledgeBaseIngestor.chunk(doc);
        assertThat(chunks).extracting(KnowledgeBaseIngestor.Chunk::section).containsExactly("First", "Second");
        assertThat(chunks.get(1).content()).isEqualTo("Beta paragraph.");
    }

    @Test
    void longSectionsAreSplitOnParagraphBoundaries() {
        String para = "x".repeat(900);
        var doc = new KnowledgeBaseIngestor.Document("T", null, "## Long\n" + para + "\n\n" + para + "\n\n" + para);
        var chunks = KnowledgeBaseIngestor.chunk(doc);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(
                KnowledgeBaseIngestor.MAX_CHUNK_CHARS));
    }

    @Test
    void identicalChunksHashIdentically() {
        var a = new KnowledgeBaseIngestor.Chunk("S", "body");
        var b = new KnowledgeBaseIngestor.Chunk("S", "body");
        assertThat(a.hash()).isEqualTo(b.hash()).hasSize(64);
    }
}
