package internal

import (
	"crypto/rsa"
	"fmt"
	"os"
	"time"

	"github.com/gbrlsnchs/jwt"
	"github.com/google/uuid"
)

func GenerateAuthToken(key *rsa.PrivateKey, kid string, macaroon []byte, domain string) ([]byte, error) {

	jti, err := uuid.NewRandom()
	if err != nil {
		fmt.Println(err)
		os.Exit(-1)
	}

	now := time.Now()
	pl := jwt.Payload{
		Issuer:         string(macaroon),
		Subject:        string(macaroon),
		Audience:       jwt.Audience{fmt.Sprintf("https://%s/api/v1/Token/auth", domain)},
		ExpirationTime: jwt.NumericDate(now.Add(5 * time.Minute)),
		IssuedAt:       jwt.NumericDate(now),
		JWTID:          jti.String(),
	}

	alg := jwt.NewRS384(jwt.RSAPrivateKey(key))

	return jwt.Sign(pl, alg, jwt.KeyID(kid))
}
