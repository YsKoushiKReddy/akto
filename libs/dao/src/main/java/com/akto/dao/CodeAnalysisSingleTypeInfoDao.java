package com.akto.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;

public class CodeAnalysisSingleTypeInfoDao extends AccountsContextDaoWithRbac<SingleTypeInfo> {

    public static final CodeAnalysisSingleTypeInfoDao instance = new CodeAnalysisSingleTypeInfoDao();

    @Override
    public String getCollName() {
        return "code_analysis_single_type_infos";
    }

    @Override
    public Class<SingleTypeInfo> getClassT() {
        return SingleTypeInfo.class;
    }

    public Map<ApiInfo.ApiInfoKey, List<String>> fetchRequestParameters(List<ApiInfo.ApiInfoKey> apiInfoKeys) {
        Map<ApiInfo.ApiInfoKey, List<String>> result = new HashMap<>();
        if (apiInfoKeys == null || apiInfoKeys.isEmpty()) return result;

        /*
         * Since singleTypeInfo is the implemented class for this and SingleTypeInfoDao.
         * reusing the filters here.
         */
        List<Bson> pipeline = SingleTypeInfoDao.instance.createPipelineForFetchRequestParams(apiInfoKeys);

        MongoCursor<BasicDBObject> stiCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (stiCursor.hasNext()) {
            BasicDBObject next = stiCursor.next();
            BasicDBObject id = (BasicDBObject) next.get("_id");
            Object paramsObj = next.get("params");
            List<String> params = new ArrayList<>();
            if (paramsObj instanceof List<?>) {
                for (Object param : (List<?>) paramsObj) {
                    if (param instanceof String) {
                        params.add((String) param);
                    }
                }
            }
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(id.getInt("apiCollectionId"), id.getString("url"), URLMethods.Method.fromString(id.getString("method")));
            result.put(apiInfoKey, params);
        }
        return result;
    }    

    @Override
    public String getFilterKeyString() {
        return SingleTypeInfo._API_COLLECTION_ID;
    }
}
