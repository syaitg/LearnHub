import request from "@/utils/request.js";

// 上传图片或普通文件，使用较长超时时间避免文件上传过程中请求提前结束
export const uploadFile = (data) =>
  request({
    url: `/ms/files`,
    method: "post",
    data,
    timeout: 30000,
  });
// 获取列表
export const getMedia = (params) =>
  request({
    url: `/ms/medias`,
    method: "get",
    params,
    timeout: 15000,
  });
// 删除媒资
export const deleteMedia = (id) =>
  request({
    url: `/ms/medias/${id}`,
    method: "delete",
  });
// 批量删除媒资视频
export const deleteMediaAll = (params) =>
  request({
    url: `/ms/medias`,
    method: "delete",
    params,
  });
// 获取上传视频的授权签名
export const getMediaUpload = (params) =>
  request({
    url: `/ms/medias/signature/upload`,
    method: "get",
    params,
    timeout: 15000,
  });
// 上传后的视传到后台
export const mediaUpload = (params) =>
  request({
    url: `/ms/medias`,
    method: "post",
    data: params,
    timeout: 15000,
  });
// 管理端获取预览视频的授权签名
export const getMediasSignature = (params) =>
  request({
    url: `/ms/medias/signature/preview`,
    method: "get",
    params,
  });
