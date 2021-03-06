
package org.meruvian.yama.social.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.persistence.NoResultException;

import org.meruvian.yama.core.user.User;
import org.meruvian.yama.social.connection.SocialConnection;
import org.meruvian.yama.social.connection.SocialConnectionRepository;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionData;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.ConnectionKey;
import org.springframework.social.connect.ConnectionRepository;
import org.springframework.social.connect.NoSuchConnectionException;
import org.springframework.social.connect.NotConnectedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Transactional(readOnly = true)
public class SocialConnectionService implements ConnectionRepository {
	private ServiceProviderConnectionMapper connectionMapper = new ServiceProviderConnectionMapper();
	private String userId;
	private SocialConnectionRepository connectionRepository;
	private ConnectionFactoryLocator connectionFactoryLocator;
	private TextEncryptor textEncryptor;
	
	public SocialConnectionService(String userId, SocialConnectionRepository connectionRepository, 
			ConnectionFactoryLocator connectionFactoryLocator, TextEncryptor textEncryptor) {
		this.userId = userId;
		this.connectionRepository = connectionRepository;
		this.connectionFactoryLocator = connectionFactoryLocator;
		this.textEncryptor = textEncryptor;
	}
	
	@Override
	public MultiValueMap<String, Connection<?>> findAllConnections() {
		List<SocialConnection> socialConnections = connectionRepository.findByUserIdOrderByRankAsc(userId);
		
		MultiValueMap<String, Connection<?>> connections = new LinkedMultiValueMap<String, Connection<?>>();
		Set<String> registeredProviderIds = connectionFactoryLocator.registeredProviderIds();

		for (String registeredProviderId : registeredProviderIds) {
			connections.put(registeredProviderId, Collections.<Connection<?>> emptyList());
		}
		
		for (SocialConnection uc : socialConnections) {
			Connection<?> connection = connectionMapper.mapRow(uc);
			String providerId = connection.getKey().getProviderId();
			if (connections.get(providerId).size() == 0) {
				connections.put(providerId, new LinkedList<Connection<?>>());
			}
			connections.add(providerId, connection);
		}
		
		return connections;
	}

	@Override
	public List<Connection<?>> findConnections(String providerId) {
		List<SocialConnection> socialConnections = connectionRepository
				.findByUserIdAndProviderOrderByRankAsc(userId, providerId);
		
		List<Connection<?>> connections = new ArrayList<Connection<?>>();

		for (SocialConnection connection : socialConnections) {
			connections.add(connectionMapper.mapRow(connection));
		}

		return connections;
	}

	@Override
	public <A> List<Connection<A>> findConnections(Class<A> apiType) {
		List<?> connections = findConnections(getProviderId(apiType));
		return (List<Connection<A>>) connections;
	}

	@Override
	public MultiValueMap<String, Connection<?>> findConnectionsToUsers(MultiValueMap<String, String> providerUserIds) {
		if (providerUserIds == null || providerUserIds.isEmpty()) {
			throw new IllegalArgumentException("Unable to execute find: no providerUsers provided");
		}
		
		// TODO

		return null;
	}

	@Override
	public Connection<?> getConnection(ConnectionKey connectionKey) {
		SocialConnection connection = connectionRepository
				.findByUserIdAndProviderAndProviderUserId(userId, connectionKey.getProviderId(),
						connectionKey.getProviderUserId());
		
		try {
			return connectionMapper.mapRow(connection);
		} catch (NoResultException e) {
			throw new NoSuchConnectionException(connectionKey);
		}
	}

	@Override
	public <A> Connection<A> getConnection(Class<A> apiType,
			String providerUserId) {
		String providerId = getProviderId(apiType);
		return (Connection<A>) getConnection(new ConnectionKey(providerId, providerUserId));
	}

	@Override
	public <A> Connection<A> getPrimaryConnection(Class<A> apiType) {
		String providerId = getProviderId(apiType);
		Connection<A> connection = (Connection<A>) findPrimaryConnection(providerId);
		
		if (connection == null) {
			throw new NotConnectedException(providerId);
		}
		
		return connection;
	}

	@Override
	public <A> Connection<A> findPrimaryConnection(Class<A> apiType) {
		String providerId = getProviderId(apiType);
		return (Connection<A>) findPrimaryConnection(providerId);
	}

	@Override
	@Transactional
	public void addConnection(Connection<?> connection) {
		ConnectionData data = connection.createData();
		int rank = connectionRepository.getRank(userId, data.getProviderId());

		User user = new User();
		user.setId(userId);
		
		SocialConnection userConnection = new SocialConnection();
		userConnection.setUser(user);
		userConnection.setProvider(data.getProviderId());
		userConnection.setProviderUserId(data.getProviderUserId());
		userConnection.setRank(rank);
		userConnection.setDisplayName(data.getDisplayName());
		userConnection.setProfileUrl(data.getProfileUrl());
		userConnection.setImageUrl(data.getImageUrl());
		userConnection.setAccessToken(encrypt(data.getAccessToken()));
		userConnection.setSecret(encrypt(data.getSecret()));
		userConnection.setRefreshToken(encrypt(data.getRefreshToken()));
		userConnection.setExpireTime(data.getExpireTime());

		connectionRepository.save(userConnection);
	}

	@Override
	@Transactional
	public void updateConnection(Connection<?> connection) {
		ConnectionData data = connection.createData();
		SocialConnection userConnection = connectionRepository
				.findByUserIdAndProviderAndProviderUserId(userId, data.getProviderId(),
						data.getProviderUserId());

		userConnection.setDisplayName(data.getDisplayName());
		userConnection.setProfileUrl(data.getProfileUrl());
		userConnection.setImageUrl(data.getImageUrl());
		userConnection.setAccessToken(encrypt(data.getAccessToken()));
		userConnection.setSecret(encrypt(data.getSecret()));
		userConnection.setRefreshToken(encrypt(data.getRefreshToken()));
		userConnection.setExpireTime(data.getExpireTime());

		connectionRepository.save(userConnection);
	}

	@Override
	@Transactional
	public void removeConnections(String providerId) {
		List<SocialConnection> connections = connectionRepository
				.findByUserIdAndProvider(userId, providerId);
		for (SocialConnection connection : connections) {
			connectionRepository.delete(connection);
		}
	}

	@Override
	public void removeConnection(ConnectionKey connectionKey) {
		SocialConnection connection = connectionRepository.findByUserIdAndProviderAndProviderUserId(
				userId, connectionKey.getProviderId(), connectionKey.getProviderUserId());
		connectionRepository.delete(connection);
	}

	private <A> String getProviderId(Class<A> apiType) {
		return connectionFactoryLocator.getConnectionFactory(apiType).getProviderId();
	}

	private String encrypt(String text) {
		return text != null ? textEncryptor.encrypt(text) : text;
	}

	private Connection<?> findPrimaryConnection(String providerId) {
		List<SocialConnection> socialConnections = connectionRepository.findByUserIdAndProviderAndRank(
				userId, providerId, 1);
		
		List<Connection<?>> connections = new ArrayList<Connection<?>>();
		for (SocialConnection connection : socialConnections) {
			connections.add(connectionMapper.mapRow(connection));
		}

		if (connections.size() > 0) {
			return connections.get(0);
		} else {
			return null;
		}
	}
	
	private final class ServiceProviderConnectionMapper {
		public Connection<?> mapRow(SocialConnection connection) {
			ConnectionData connectionData = mapConnectionData(connection);
			ConnectionFactory<?> connectionFactory = connectionFactoryLocator
					.getConnectionFactory(connectionData.getProviderId());
			return connectionFactory.createConnection(connectionData);
		}

		private ConnectionData mapConnectionData(SocialConnection connection) {
			return new ConnectionData(connection.getProvider().toString().toLowerCase(),
					connection.getProviderUserId(), connection.getDisplayName(),
					connection.getProfileUrl(), connection.getImageUrl(), decrypt(connection.getAccessToken()),
					decrypt(connection.getSecret()), decrypt(connection.getRefreshToken()),
					expireTime(connection.getExpireTime()));
		}

		private String decrypt(String encryptedText) {
			return encryptedText != null ? textEncryptor.decrypt(encryptedText) : encryptedText;
		}

		private Long expireTime(long expireTime) {
			return expireTime == 0 ? null : expireTime;
		}
	}
}
