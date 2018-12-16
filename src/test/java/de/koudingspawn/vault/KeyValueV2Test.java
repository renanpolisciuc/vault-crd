package de.koudingspawn.vault;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import de.koudingspawn.vault.crd.Vault;
import de.koudingspawn.vault.crd.VaultSpec;
import de.koudingspawn.vault.crd.VaultType;
import de.koudingspawn.vault.kubernetes.EventHandler;
import de.koudingspawn.vault.kubernetes.scheduler.impl.KeyValueV2Refresh;
import de.koudingspawn.vault.vault.communication.SecretNotAccessibleException;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static de.koudingspawn.vault.Constants.COMPARE_ANNOTATION;
import static de.koudingspawn.vault.Constants.LAST_UPDATE_ANNOTATION;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {
                "kubernetes.vault.url=http://localhost:8207/v1/"
        },
        classes = {
                TestConfiguration.class
        }
)
public class KeyValueV2Test {

    @ClassRule
    public static WireMockClassRule wireMockClassRule =
            new WireMockClassRule(wireMockConfig().port(8207));

    @Rule
    public WireMockClassRule instanceRule = wireMockClassRule;

    @Autowired
    public EventHandler handler;

    @Autowired
    public KeyValueV2Refresh keyValueV2Refresh;

    @Autowired
    KubernetesClient testClient;

    @Before
    public void before() {
        WireMock.resetAllScenarios();
        testClient.secrets().inAnyNamespace().delete();

        TestHelper.generateLookupSelfStub();
    }

    @Test
    public void shouldGenerateSimpleSecretFromVaultCustomResource() {
        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("simple").withNamespace("default").build());
        VaultSpec vaultSpec = new VaultSpec();
        vaultSpec.setType(VaultType.KEYVALUEV2);
        vaultSpec.setPath("secret/simple");
        vault.setSpec(vaultSpec);

        stubFor(get(urlPathMatching("/v1/secret/data/simple"))
            .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\n" +
                    "  \"request_id\": \"1cfee2a6-318a-ea12-f5b5-6fd52d74d2c6\",\n" +
                    "  \"lease_id\": \"\",\n" +
                    "  \"renewable\": false,\n" +
                    "  \"lease_duration\": 0,\n" +
                    "  \"data\": {\n" +
                    "    \"data\": {\n" +
                    "      \"key\": \"value\"\n" +
                    "    },\n" +
                    "    \"metadata\": {\n" +
                    "      \"created_time\": \"2018-12-10T18:59:53.337997525Z\",\n" +
                    "      \"deletion_time\": \"\",\n" +
                    "      \"destroyed\": false,\n" +
                    "      \"version\": 1\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"wrap_info\": null,\n" +
                    "  \"warnings\": null,\n" +
                    "  \"auth\": null\n" +
                    "}")));

        handler.addHandler(vault);

        Secret secret = testClient.secrets().inNamespace("default").withName("simple").get();
        assertEquals("simple", secret.getMetadata().getName());
        assertEquals("default", secret.getMetadata().getNamespace());
        assertEquals("Opaque", secret.getType());
        assertEquals("dmFsdWU=", secret.getData().get("key"));
        assertNotNull(secret.getMetadata().getAnnotations().get("vault.koudingspawn.de" + LAST_UPDATE_ANNOTATION));
        assertEquals("dYxf3NXqZ1l2d1YL1htbVBs6EUot33VjoBUUrBJg1eY=", secret.getMetadata().getAnnotations().get("vault.koudingspawn.de" + COMPARE_ANNOTATION));
    }

    @Test
    public void shouldCheckIfSimpleSecretHasChangedAndReturnTrue() throws SecretNotAccessibleException {
        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("simple").withNamespace("default").build());
        VaultSpec vaultSpec = new VaultSpec();
        vaultSpec.setType(VaultType.KEYVALUEV2);
        vaultSpec.setPath("secret/simple");
        vault.setSpec(vaultSpec);

        stubFor(get(urlPathMatching("/v1/secret/data/simple"))
                .inScenario("Vault secret change")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("First request done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"1cfee2a6-318a-ea12-f5b5-6fd52d74d2c6\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 0,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"key\": \"value\"\n" +
                                "    },\n" +
                                "    \"metadata\": {\n" +
                                "      \"created_time\": \"2018-12-10T18:59:53.337997525Z\",\n" +
                                "      \"deletion_time\": \"\",\n" +
                                "      \"destroyed\": false,\n" +
                                "      \"version\": 1\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        stubFor(get(urlPathMatching("/v1/secret/data/simple"))
                .inScenario("Vault secret change")
                .whenScenarioStateIs("First request done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"1cfee2a6-318a-ea12-f5b5-6fd52d74d2c6\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 0,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"key\": \"value1\"\n" +
                                "    },\n" +
                                "    \"metadata\": {\n" +
                                "      \"created_time\": \"2018-12-10T18:59:53.337997525Z\",\n" +
                                "      \"deletion_time\": \"\",\n" +
                                "      \"destroyed\": false,\n" +
                                "      \"version\": 1\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        handler.addHandler(vault);

        assertTrue(keyValueV2Refresh.refreshIsNeeded(vault));
    }

    @Test
    public void shouldCheckIfSimpleSecretHasChangedAndReturnFalse() throws SecretNotAccessibleException {
        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("simple").withNamespace("default").build());
        VaultSpec vaultSpec = new VaultSpec();
        vaultSpec.setType(VaultType.KEYVALUEV2);
        vaultSpec.setPath("secret/simple");
        vault.setSpec(vaultSpec);

        stubFor(get(urlPathMatching("/v1/secret/data/simple"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"1cfee2a6-318a-ea12-f5b5-6fd52d74d2c6\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 0,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"key\": \"value\"\n" +
                                "    },\n" +
                                "    \"metadata\": {\n" +
                                "      \"created_time\": \"2018-12-10T18:59:53.337997525Z\",\n" +
                                "      \"deletion_time\": \"\",\n" +
                                "      \"destroyed\": false,\n" +
                                "      \"version\": 1\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        handler.addHandler(vault);

        assertFalse(keyValueV2Refresh.refreshIsNeeded(vault));
    }

}
