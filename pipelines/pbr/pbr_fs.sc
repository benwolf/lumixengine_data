$input v_wpos, v_texcoord0 // in...

#include "common.sh"

#define M_PI 3.14159265

// copied from jmonkey unless noted otherwise https://github.com/jMonkeyEngine/jmonkeyengine/blob/cd70630502ef15e08607e78fb40204cddea945e4/jme3-core/src/main/resources/Common/MatDefs/Light/PBRLighting.frag

SAMPLER2D(u_gbuffer0, 15);
SAMPLER2D(u_gbuffer1, 14);
SAMPLER2D(u_gbuffer2, 13);
SAMPLER2D(u_gbuffer_depth, 12);
SAMPLERCUBE(u_irradiance_map, 11);
SAMPLERCUBE(u_radiance_map, 10);
SAMPLER2D(u_lut, 9);
SAMPLER2D(u_texShadowmap, 8);

uniform vec4 u_lightPosRadius;
uniform vec4 u_lightRgbAttenuation;
uniform vec4 u_ambientColor;
uniform vec4 u_lightDirFov; 
uniform mat4 u_shadowmapMatrices[4];
uniform vec4 u_fogColorDensity; 
uniform vec4 u_fogParams;

void PBR_ComputeDirectLight(vec3 normal, vec3 lightDir, vec3 viewDir,
                            vec3 lightColor, float fZero, float roughness,
                            out vec3 outDiffuse, out vec3 outSpecular)
{
    vec3 halfVec = normalize(lightDir + viewDir);
    float ndotl = saturate(dot(normal,   lightDir));
    float hdotv = saturate(dot(viewDir,  halfVec));
    float ndoth = saturate(dot(normal,   halfVec));       

    outDiffuse = vec3_splat(ndotl) * lightColor;

    float alpha = roughness * roughness;
    float alpha2 = alpha * alpha;
    float sum  = ((ndoth * ndoth) * (alpha2 - 1.0) + 1.0);
    float denom = M_PI * sum * sum;
    float D = alpha2 / denom;  

	float F_b = pow(1 - hdotv, 5);
	float k = alpha2 * 0.25;
	float inv_k = 1 - k;
	float vis = 1/(hdotv * hdotv * inv_k + k);
    float specular = ndotl * D * (fZero * vis + (1-fZero) * F_b * vis); 
	
    outSpecular = vec3_splat(specular) * lightColor;
}


vec3 PBR_ComputeIndirectDiffuse(vec3 normal, vec3 diffuseColor)
{
	return textureCube(u_irradiance_map, normal.xyz).rgb * diffuseColor.rgb;
}


// from urho
vec3 EnvBRDFApprox (vec3 SpecularColor, float Roughness, float NoV)
{
	vec4 c0 = vec4(-1, -0.0275, -0.572, 0.022 );
	vec4 c1 = vec4(1, 0.0425, 1.0, -0.04 );
	vec4 r = Roughness * c0 + c1;
	float a004 = min( r.x * r.x, exp2( -9.28 * NoV ) ) * r.x + r.y;
	vec2 AB = vec2( -1.04, 1.04 ) * a004 + r.zw;
	return SpecularColor * AB.x + AB.y;
}


vec3 PBR_ComputeIndirectSpecular(vec3 spec_color , float roughness, float ndotv, vec3 reflected_vec)
{
    float Lod = roughness * 8;
    vec3 PrefilteredColor =  textureCubeLod(u_radiance_map, reflected_vec.xyz, Lod).rgb;    
    return PrefilteredColor * EnvBRDFApprox(spec_color, roughness, ndotv);
}


void main()
{
	float f0 = 0.04;
	
	vec4 gbuffer1_val = texture2D(u_gbuffer1, v_texcoord0);
	vec3 normal = normalize(gbuffer1_val.xyz * 2 - 1);
	vec4 albedo = texture2D(u_gbuffer0, v_texcoord0);
	float roughness = albedo.w;
	float metallic = gbuffer1_val.w;
	vec4 value2 = texture2D(u_gbuffer2, v_texcoord0) * 64.0;
	
	vec3 wpos = getViewPosition(u_gbuffer_depth, u_camInvViewProj, v_texcoord0);

	vec4 camera_wpos = mul(u_camInvView, vec4(0, 0, 0, 1));
	vec3 view = normalize(camera_wpos.xyz - wpos);
	
	vec4 specularColor = (f0 - f0 * metallic) + albedo * metallic;
	vec4 diffuseColor = albedo - albedo * metallic;
	
	float ndotv = saturate(dot(normal, view));
	vec3 direct_diffuse;
	vec3 direct_specular;
	PBR_ComputeDirectLight(normal, -u_lightDirFov.xyz, view, u_lightRgbAttenuation.rgb, f0, roughness, direct_diffuse, direct_specular);

	vec3 indirect_diffuse = PBR_ComputeIndirectDiffuse(normal, diffuseColor.rgb);
	vec3 rv = reflect(-view.xyz, normal.xyz);
	vec3 indirect_specular = PBR_ComputeIndirectSpecular(specularColor, roughness, ndotv, rv);
	
	float ndotl = -dot(normal, u_lightDirFov.xyz);
	float shadow = directionalLightShadow(u_texShadowmap, u_shadowmapMatrices, vec4(wpos, 1), ndotl);
	float fog_factor = getFogFactor(camera_wpos.xyz / camera_wpos.w, u_fogColorDensity.w, wpos.xyz, u_fogParams);
	vec3 lighting = 
		direct_diffuse * diffuseColor.rgb * shadow + 
		direct_specular * specularColor.rgb * shadow + 
		indirect_diffuse + 
		indirect_specular +
		0
		;
	gl_FragColor.rgb = mix(lighting, u_fogColorDensity.rgb, fog_factor);
	gl_FragColor.w = 1;
}