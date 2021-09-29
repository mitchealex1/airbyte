/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.bigquery;

import static io.airbyte.db.DataTypeUtils.returnNullIfInvalid;
import static io.airbyte.db.DataTypeUtils.toISO8601String;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValue.Attribute;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import io.airbyte.commons.json.Jsons;
import io.airbyte.db.DataTypeUtils;
import io.airbyte.db.SourceOperations;
import io.airbyte.protocol.models.JsonSchemaPrimitive;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigQuerySourceOperations implements SourceOperations<BigQueryResultSet, StandardSQLTypeName> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BigQuerySourceOperations.class);

  private final DateFormat BIG_QUERY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private final DateFormat BIG_QUERY_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
  private final DateFormat BIG_QUERY_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS z");

  @Override
  public JsonNode rowToJson(BigQueryResultSet bigQueryResultSet) {
    ObjectNode jsonNode = (ObjectNode) Jsons.jsonNode(Collections.emptyMap());
    bigQueryResultSet.getFieldList().forEach(field -> setJsonField(field, bigQueryResultSet.getRowValues().get(field.getName()), jsonNode));
    return jsonNode;
  }

  private void fillObjectNode(String fieldName, StandardSQLTypeName fieldType, FieldValue fieldValue, ObjectNode node) {
    switch (fieldType) {
      case BOOL -> node.put(fieldName, fieldValue.getBooleanValue());
      case INT64 -> node.put(fieldName, fieldValue.getLongValue());
      case FLOAT64 -> node.put(fieldName, fieldValue.getDoubleValue());
      case NUMERIC -> node.put(fieldName, fieldValue.getNumericValue());
      case BIGNUMERIC -> node.put(fieldName, returnNullIfInvalid(fieldValue::getNumericValue));
      case STRING -> node.put(fieldName, fieldValue.getStringValue());
      case BYTES -> node.put(fieldName, fieldValue.getBytesValue());
      case DATE -> node.put(fieldName, toISO8601String(getDateValue(fieldValue, BIG_QUERY_DATE_FORMAT)));
      case DATETIME -> node.put(fieldName, toISO8601String(getDateValue(fieldValue, BIG_QUERY_DATETIME_FORMAT)));
      case TIMESTAMP -> node.put(fieldName, toISO8601String(fieldValue.getTimestampValue() / 1000));
      case TIME -> node.put(fieldName, fieldValue.getStringValue());
      default -> node.put(fieldName, fieldValue.getStringValue());
    }
  }

  private void setJsonField(Field field, FieldValue fieldValue, ObjectNode node) {
    String fieldName = field.getName();
    if (fieldValue.getAttribute().equals(Attribute.PRIMITIVE)) {
      if (fieldValue.isNull()) {
        node.put(fieldName, (String) null);
      } else {
        fillObjectNode(fieldName, field.getType().getStandardType(), fieldValue, node);
      }
    } else if (fieldValue.getAttribute().equals(Attribute.REPEATED)) {
      ArrayNode arrayNode = node.putArray(fieldName);
      StandardSQLTypeName fieldType = field.getType().getStandardType();
      FieldList subFields = field.getSubFields();
      // Array of primitive
      if (subFields == null || subFields.isEmpty()) {
        fieldValue.getRepeatedValue().forEach(arrayFieldValue -> fillObjectNode(fieldName, fieldType, arrayFieldValue, arrayNode.addObject()));
        // Array of records
      } else {
        for (FieldValue arrayFieldValue : fieldValue.getRepeatedValue()) {
          int count = 0; // named get doesn't work here for some reasons.
          ObjectNode newNode = arrayNode.addObject();
          for (Field repeatedField : subFields) {
            setJsonField(repeatedField, arrayFieldValue.getRecordValue().get(count++),
                newNode);
          }
        }
      }
    } else if (fieldValue.getAttribute().equals(Attribute.RECORD)) {
      ObjectNode newNode = node.putObject(fieldName);
      FieldList subFields = field.getSubFields();
      try {
        // named get doesn't work here with nested arrays and objects; index is the only correlation between
        // field and field value
        if (subFields != null && !subFields.isEmpty()) {
          for (int i = 0; i < subFields.size(); i++) {
            setJsonField(field.getSubFields().get(i), fieldValue.getRecordValue().get(i), newNode);
          }
        }
      } catch (UnsupportedOperationException e) {
        LOGGER.error("Failed to parse Object field with name: ", fieldName, e.getMessage());
      }
    }
  }

  public Date getDateValue(FieldValue fieldValue, DateFormat dateFormat) {
    Date parsedValue = null;
    String value = fieldValue.getStringValue();
    try {
      parsedValue = dateFormat.parse(value);
    } catch (ParseException e) {
      LOGGER.error("Fail to parse date value : " + value + ". Null is returned.");
    }
    return parsedValue;
  }

  @Override
  public JsonSchemaPrimitive getType(StandardSQLTypeName bigQueryType) {
    return switch (bigQueryType) {
      case BOOL -> JsonSchemaPrimitive.BOOLEAN;
      case INT64, FLOAT64, NUMERIC, BIGNUMERIC -> JsonSchemaPrimitive.NUMBER;
      case STRING, BYTES, TIMESTAMP, DATE, TIME, DATETIME -> JsonSchemaPrimitive.STRING;
      case ARRAY -> JsonSchemaPrimitive.ARRAY;
      case STRUCT -> JsonSchemaPrimitive.OBJECT;
      default -> JsonSchemaPrimitive.STRING;
    };
  }

  private String getFormattedValue(StandardSQLTypeName paramType, String paramValue) {
    try {
      return switch (paramType) {
        case DATE -> BIG_QUERY_DATE_FORMAT.format(DataTypeUtils.DATE_FORMAT.parse(paramValue));
        case DATETIME -> BIG_QUERY_DATETIME_FORMAT
            .format(DataTypeUtils.DATE_FORMAT.parse(paramValue));
        case TIMESTAMP -> BIG_QUERY_TIMESTAMP_FORMAT
            .format(DataTypeUtils.DATE_FORMAT.parse(paramValue));
        default -> paramValue;
      };
    } catch (ParseException e) {
      throw new RuntimeException("Fail to parse value " + paramValue + " to type " + paramType.name());
    }
  }

  public QueryParameterValue getQueryParameter(StandardSQLTypeName paramType, String paramValue) {
    String value = getFormattedValue(paramType, paramValue);
    LOGGER.info("Query parameter for set : " + value + ". Type: " + paramType.name());
    return QueryParameterValue.newBuilder().setType(paramType).setValue(value).build();
  }

}
