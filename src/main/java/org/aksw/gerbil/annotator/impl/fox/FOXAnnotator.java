package org.aksw.gerbil.annotator.impl.fox;

import java.net.URLDecoder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aksw.gerbil.annotator.OKETask1Annotator;
import org.aksw.gerbil.annotator.impl.AbstractAnnotator;
import org.aksw.gerbil.config.GerbilConfiguration;
import org.aksw.gerbil.datatypes.ErrorTypes;
import org.aksw.gerbil.exceptions.GerbilException;
import org.aksw.gerbil.transfer.nif.Document;
import org.aksw.gerbil.transfer.nif.MeaningSpan;
import org.aksw.gerbil.transfer.nif.Span;
import org.aksw.gerbil.transfer.nif.TypedSpan;
import org.aksw.gerbil.transfer.nif.data.DocumentImpl;
import org.aksw.gerbil.transfer.nif.data.TypedNamedEntity;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FOXAnnotator extends AbstractAnnotator implements OKETask1Annotator {

    private static final String FOX_SERVICE_URL_PARAMETER_KEY = "org.aksw.gerbil.annotators.FOXAnnotatorConfig.serviceUrl";

    private static final Logger LOGGER = LoggerFactory.getLogger(FOXAnnotator.class);

    private static final String PERSON_TYPE_URI = "scmsann:PERSON";
    private static final String LOCATION_TYPE_URI = "scmsann:LOCATION";
    private static final String ORGANIZATION_TYPE_URI = "scmsann:ORGANIZATION";
    private static final String DOLCE_PERSON_TYPE_URI = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Person";
    private static final String DOLCE_LOCATION_TYPE_URI = "http://www.ontologydesignpatterns.org/ont/d0.owl#Location";
    private static final String DOLCE_ORGANIZATION_TYPE_URI = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Organization";

    @Deprecated
    public static final String NAME = "FOX";

    private String serviceUrl;

    public FOXAnnotator() throws GerbilException {
        serviceUrl = GerbilConfiguration.getInstance().getString(FOX_SERVICE_URL_PARAMETER_KEY);
        if (serviceUrl == null) {
            throw new GerbilException("Couldn't load the needed property \"" + FOX_SERVICE_URL_PARAMETER_KEY + "\".",
                    ErrorTypes.ANNOTATOR_LOADING_ERROR);
        }
    }

    public FOXAnnotator(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    @Override
    public List<MeaningSpan> performExtraction(Document document) throws GerbilException {
        return requestAnnotations(document).getMarkings(MeaningSpan.class);
    }

    @Override
    public List<MeaningSpan> performLinking(Document document) throws GerbilException {
        return requestAnnotations(document).getMarkings(MeaningSpan.class);
    }

    @Override
    public List<Span> performRecognition(Document document) throws GerbilException {
        return requestAnnotations(document).getMarkings(Span.class);
    }

    @Override
    public List<TypedSpan> performTyping(Document document) throws GerbilException {
        return requestAnnotations(document).getMarkings(TypedSpan.class);
    }

    @Override
    public List<TypedNamedEntity> performTask1(Document document) throws GerbilException {
        return requestAnnotations(document).getMarkings(TypedNamedEntity.class);
    }

    protected Document requestAnnotations(Document document) throws GerbilException {
        Document resultDoc = new DocumentImpl(document.getText(), document.getDocumentURI());
        try {
            // request FOX
            Response response = Request
                    .Post(serviceUrl)
                    .addHeader("Content-type", "application/json")
                    .addHeader("Accept-Charset", "UTF-8")
                    .body(new StringEntity(new JSONObject().put("input", document.getText()).put("type", "text")
                            .put("task", "ner").put("output", "JSON-LD").toString(), ContentType.APPLICATION_JSON))
                    .execute();

            HttpResponse httpResponse = response.returnResponse();
            HttpEntity entry = httpResponse.getEntity();

            String content = IOUtils.toString(entry.getContent(), "UTF-8");
            EntityUtils.consume(entry);

            // parse results
            JSONObject outObj = new JSONObject(content);
            if (outObj.has("@graph")) {

                JSONArray graph = outObj.getJSONArray("@graph");
                for (int i = 0; i < graph.length(); i++) {
                    parseType(graph.getJSONObject(i), resultDoc);
                }
            } else {
                parseType(outObj, resultDoc);
            }
        } catch (GerbilException e) {
            throw e;
        } catch (Exception e) {
            throw new GerbilException("Got an exception while communicating with the FOX web service.", e,
                    ErrorTypes.UNEXPECTED_EXCEPTION);
        }
        return resultDoc;
    }

    protected void parseType(JSONObject entity, Document resultDoc) throws GerbilException {
        try {

            if (entity != null && entity.has("means") && entity.has("beginIndex") && entity.has("ann:body")) {

                String uri = entity.getString("means");
                if (uri.startsWith("dbpedia:")) {
                    uri = "http://dbpedia.org/resource/" + uri.substring(8);
                }
                String body = entity.getString("ann:body");
                Object begin = entity.get("beginIndex");
                Object typeObject = entity.get("@type");
                Set<String> types = new HashSet<String>();
                if (typeObject instanceof JSONArray) {
                    JSONArray typeArray = (JSONArray) typeObject;
                    for (int i = 0; i < typeArray.length(); ++i) {
                        addType(typeArray.getString(i), types);
                    }
                } else {
                    addType(typeObject.toString(), types);
                }
                uri = URLDecoder.decode(uri, "UTF-8");
                if (begin instanceof JSONArray) {
                    // for all indices
                    for (int i = 0; i < ((JSONArray) begin).length(); ++i) {
                        resultDoc.addMarking(new TypedNamedEntity(Integer.valueOf(((JSONArray) begin).getString(i)),
                                body.length(), uri, types));
                    }
                } else if (begin instanceof String) {
                    resultDoc.addMarking(new TypedNamedEntity(Integer.valueOf((String) begin), body.length(), uri,
                            types));
                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Couldn't find index");
                }
            }
        } catch (Exception e) {
            throw new GerbilException("Got an Exception while parsing the response of FOX.", e,
                    ErrorTypes.UNEXPECTED_EXCEPTION);
        }
    }

    protected void addType(String typeString, Set<String> types) {
        switch (typeString) {
        case PERSON_TYPE_URI: {
            types.add(DOLCE_PERSON_TYPE_URI);
            break;
        }
        case LOCATION_TYPE_URI: {
            types.add(DOLCE_LOCATION_TYPE_URI);
            break;
        }
        case ORGANIZATION_TYPE_URI: {
            types.add(DOLCE_ORGANIZATION_TYPE_URI);
            break;
        }
        }
        types.add(typeString.replaceFirst("scmsann:", "http://ns.aksw.org/scms/annotations/"));
    }
}