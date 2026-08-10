import request from '@/utils/request'

export function getCookies(params) {
  return request({
    url: '/spiderCookiePool/page',
    method: 'get',
    params: {
      pageNumber: params.page || 1,
      pageSize: params.size || 10,
      site: params.site || undefined,
      status: params.status !== null ? params.status : undefined
    }
  })
}

export function getCookieById(id) {
  return request({
    url: `/spiderCookiePool/getInfo/${id}`,
    method: 'get'
  })
}

export function createCookie(data) {
  return request({
    url: '/spiderCookiePool/save',
    method: 'post',
    data
  })
}

export function updateCookie(id, data) {
  return request({
    url: '/spiderCookiePool/update',
    method: 'put',
    data: { ...data, id }
  })
}

export function deleteCookie(id) {
  return request({
    url: `/spiderCookiePool/remove/${id}`,
    method: 'delete'
  })
}

export function verifyCookie(id) {
  return request({
    url: `/spiderCookiePool/verify/${id}`,
    method: 'post'
  })
}
