const mustache = require('/lib/mustache');
const portal = require('/lib/xp/portal');
const nodeLib  = require('/lib/xp/node');

function parseQueryString(queryString) {
  if (!queryString) {
    return null;
  }

  return queryString.split('&').reduce((acc, pair) => {
    const parts = pair.split('=');
    const key = parts[0];
    const value = parts[1];
    
    if (!acc[key]) {
      acc[key] = {};
    }
    
    acc[key][value] = true;
    return acc;
  }, {});
}

const getUniqueParams = (urlBuckets) => {
  const queryStrings = urlBuckets.map(url => url.key ? url.key.split('?')[1] : '');

  const parsedQueryStrings = queryStrings.map(parseQueryString).filter((parsed) => {
    return parsed !== null;
  });
  
  let allParams = {};
  parsedQueryStrings.forEach((parsed) => {
    for (let param in parsed) {
      if (parsed.hasOwnProperty(param)) {
        allParams[param] = true;
      }
    }
  });

  let result = [];
  for (let param in allParams) {
    if (allParams.hasOwnProperty(param)) {
      let uniqueValues = {};
      parsedQueryStrings.forEach((parsed) => {
        if (parsed[param]) {
          for (let value in parsed[param]) {
            if (parsed[param].hasOwnProperty(value)) {
              uniqueValues[value] = true;
            }
          }
        }
      });
      const uniqueValuesArray = Object.keys(uniqueValues);
      
      result.push({
        paramName: param,
        uniqueValuesCount: uniqueValuesArray.length
      });
      result.sort((a, b) => {
        // Sort descending
        return b.uniqueValuesCount - a.uniqueValuesCount;
      });
    }
  }
  
  return result;
}

const renderPathStats = (req) => {
  const view = resolve('pathstats.html');
  const path = req.params.path;

  const boosterRepo = nodeLib.connect({
    repoId: 'com.enonic.app.booster',
    branch: 'master'
  });

  const pathUrls = boosterRepo.query({
    start: 0,
    count: 0,
    query: {
      'term': {
        'field': 'path',
        'value': path
      }
    },
    aggregations: {
      urls: {
        terms: {
          field: 'url',
          size: 10000
        }
      }
    }
  });
  
  const uniqueParams = getUniqueParams(pathUrls.aggregations.urls.buckets);
  
  if (uniqueParams.length) {
    return {
      contentType: 'text/html',
      body: mustache.render(view, {
        assetsUri: portal.assetUrl({ path: ''}),
        path,
        uniqueParams
      })
    }
  }

  return {
    contentType: 'text/html',
    body: '<p>(no querystring params in cache)</p>'
  };
}

exports.get = (req) => renderPathStats(req);
