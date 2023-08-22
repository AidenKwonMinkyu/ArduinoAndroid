package phycastle2.arduinogather

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("gatheritem/")
    fun postData(@Body data: GatherReq): Call<GatherRes>
}