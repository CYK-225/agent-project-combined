import request from '@/utils/request'

export function getTasksPage(params) {
  return request({
    url: '/taskInfo/page',
    method: 'get',
    params: {
      pageNumber: params.page || 1,
      pageSize: params.size || 10
    }
  })
}

export function getTasksByCompanyName(companyName) {
  return request({
    url: '/taskInfo/listByCompanyName',
    method: 'get',
    params: {
      companyName: companyName || undefined
    }
  })
}

export function getTasksByDateRange(params) {
  return request({
    url: '/taskInfo/pageByDate',
    method: 'get',
    params: {
      pageNumber: params.page || 1,
      pageSize: params.size || 10,
      startDate: params.startDate || undefined,
      endDate: params.endDate || undefined
    }
  })
}
