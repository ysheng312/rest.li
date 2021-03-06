/*
   Copyright (c) 2014 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.internal.common;


import com.linkedin.data.DataList;
import com.linkedin.data.DataMap;
import com.linkedin.data.collections.CheckedUtil;
import com.linkedin.data.schema.DataSchema;
import com.linkedin.data.schema.DataSchemaUtil;
import com.linkedin.data.schema.TyperefDataSchema;
import com.linkedin.data.template.DataTemplateUtil;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.TyperefInfo;
import com.linkedin.jersey.api.uri.UriComponent;
import com.linkedin.restli.common.ComplexKeySpec;
import com.linkedin.restli.common.ComplexResourceKey;
import com.linkedin.restli.common.CompoundKey;
import com.linkedin.restli.common.ProtocolVersion;
import com.linkedin.restli.common.TypeSpec;

import java.util.List;
import java.util.Map;


/**
 * Utilities for key-typed responses
 */
public class ResponseUtils
{
  @SuppressWarnings("unchecked")
  public static <T> T convertKey(String rawKey,
                                 TypeSpec<T> key,
                                 Map<String, CompoundKey.TypeInfo> keyParts,
                                 ComplexKeySpec<?, ?> complexKeyType,
                                 ProtocolVersion version)
  {
    Class<? extends T> keyBindingClass = key.getType();
    Object result;

    if (TyperefInfo.class.isAssignableFrom(key.getType()))
    {
      TyperefDataSchema schema = (TyperefDataSchema)key.getSchema();
      DataSchema.Type dereferencedType = schema.getDereferencedType();
      if (!schema.getDereferencedDataSchema().isPrimitive())
      {
        throw new IllegalArgumentException("Compound key type must dereference to a primitive type.");
      }
      keyBindingClass = (Class<? extends T>)TyperefUtils.getJavaClassForSchema(schema);
      if(keyBindingClass == null)
      {
        keyBindingClass = (Class<? extends T>)dereferencedType.getClass();
      }
      result = ValueConverter.coerceString(rawKey, DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchemaClass(dereferencedType));
    }
    else if (CompoundKey.class.isAssignableFrom(key.getType()))
    {
      DataMap keyDataMap;
      if (version.compareTo(AllProtocolVersions.RESTLI_PROTOCOL_2_0_0.getProtocolVersion()) >= 0)
      {
        try
        {
          keyDataMap = (DataMap) URIElementParser.parse(rawKey);
        }
        catch (PathSegment.PathSegmentSyntaxException e)
        {
          throw new IllegalStateException(rawKey + " is not a valid value for the resource key", e);
        }
      }
      else
      {
        keyDataMap = parseKey(rawKey);
      }

      result = CompoundKey.fromValues(keyDataMap, keyParts);
    }
    else if (ComplexResourceKey.class.isAssignableFrom(key.getType()))
    {
      try
      {
        ComplexResourceKey<RecordTemplate, RecordTemplate> complexResourceKey =
                ComplexResourceKey.parseString(rawKey, complexKeyType, version);
        result = QueryParamsDataMap.fixUpComplexKeySingletonArray(complexResourceKey);
      }
      catch (PathSegment.PathSegmentSyntaxException e)
      {
        throw new IllegalStateException(rawKey + " is not a valid value for the resource key", e);
      }
    }
    else
    {
      try
      {
        result = ValueConverter.coerceString(rawKey, key.getType());
      }
      catch (IllegalArgumentException e)
      {
        throw new IllegalStateException(key.getType().getName() + " is not supported as a key type for BatchKVResponse", e);
      }
    }

    return DataTemplateUtil.coerceOutput(result, keyBindingClass);
  }

  //TODO: replace with generic QueryParam <=> DataMap codec
  private static DataMap parseKey(String rawKey)
  {
    Map<String, List<String>> fields = UriComponent.decodeQuery(rawKey, true);
    DataMap result = new DataMap((int)Math.ceil(fields.size()/0.75f));
    for (Map.Entry<String, List<String>> entry : fields.entrySet())
    {
      if (entry.getValue().size() == 1)
      {
        result.put(entry.getKey(), entry.getValue().get(0));
      }
      else
      {
        CheckedUtil.putWithoutChecking(result, entry.getKey(), new DataList(entry.getValue()));
      }
    }
    return result;
  }
}
