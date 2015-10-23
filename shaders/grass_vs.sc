$input a_position, a_normal, a_tangent, a_texcoord0, i_data0, i_data1, i_data2, i_data3
$output v_wpos, v_common, v_texcoord0

#include "common.sh"


void main()
{
	mat4 model;
	model[0] = i_data0;
	model[1] = i_data1;
	model[2] = i_data2;
	model[3] = i_data3;

	model = transpose(model);
	
	vec3 view = mul(u_invView, vec4(0.0, 0.0, 0.0, 1.0)).xyz - mul(model, vec4(a_position, 1.0) ).xyz;
	float scale = 1; //clamp(1 - (length(view) - 10)/10, 0, 1);
    v_wpos = mul(model, vec4(a_position * scale, 1.0) ).xyz;

	v_common = vec3(a_position.y, a_position.y, a_position.y);
	v_texcoord0 = a_texcoord0;

	gl_Position =  mul(u_viewProj, vec4(v_wpos, 1.0) );
}
