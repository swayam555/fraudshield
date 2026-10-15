package com.fraudshield.cases.kb;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ingests the real knowledge base into real pgvector and checks retrieval quality end to end. */
@Testcontainers
class HybridRetrieverIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static HybridRetriever retriever;
    static KnowledgeBaseIngestor ingestor;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        JdbcClient jdbc = JdbcClient.create(ds);
        Embedder embedder = new Embedder();
        ingestor = new KnowledgeBaseIngestor(jdbc, embedder, new TransactionTemplate(new DataSourceTransactionManager(ds)));
        ingestor.ingestOnStartup();
        retriever = new HybridRetriever(jdbc, embedder);
    }

    @Test
    void exactRegulatoryTermsAreFoundLexically() {
        List<Passage> hits = retriever.search("STR FIU-IND deadline", 3);
        assertThat(hits.get(0).section()).contains("Suspicious Transaction Reports");
        assertThat(hits.get(0).lexicalRank()).isNotNull();
    }

    @Test
    void paraphrasesAreFoundSemantically() {
        // No keyword overlap with "money mule": dense retrieval has to carry this one.
        List<Passage> hits = retriever.search("someone recruited to receive stolen money and quickly send it onwards", 3);
        assertThat(hits).anySatisfy(p -> assertThat(p.section().toLowerCase()).contains("mule"));
    }

    @Test
    void reingestionIsIdempotent() throws Exception {
        long before = retriever.chunkCount();
        ingestor.ingestOnStartup();
        assertThat(retriever.chunkCount()).isEqualTo(before);
    }
}
