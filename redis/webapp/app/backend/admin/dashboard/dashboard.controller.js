'use strict';

angular.module('redisApp').controller('DashboardCtrl', function ($scope, Dashboards) {
	Dashboards.metrics.one().get().then(function(metrics) {
		$scope.metrics = [];

		angular.forEach(metrics, function(value, key) {
			if (!angular.isFunction(value)) {
				this.push({ key: key, value: value });
			}
		}, $scope.metrics);
	});
});
