package com.palantir.product;

import com.palantir.conjure.java.lib.Bytes;
import com.palantir.tokens.auth.AuthHeader;
import java.util.Optional;
import javax.annotation.Generated;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

@Generated("com.palantir.conjure.java.services.Retrofit2ServiceGenerator")
public interface EteBinaryServiceRetrofit {
    @POST("./binary")
    @Headers({"hr-path-template: /binary", "Accept: application/octet-stream"})
    @Streaming
    Call<ResponseBody> postBinary(
            @Header("Authorization") AuthHeader authHeader, @Body RequestBody body);

    @GET("./binary/optional/present")
    @Headers({"hr-path-template: /binary/optional/present", "Accept: application/json"})
    Call<Optional<Bytes>> getOptionalBinaryPresent(@Header("Authorization") AuthHeader authHeader);

    @GET("./binary/optional/empty")
    @Headers({"hr-path-template: /binary/optional/empty", "Accept: application/json"})
    Call<Optional<Bytes>> getOptionalBinaryEmpty(@Header("Authorization") AuthHeader authHeader);
}
