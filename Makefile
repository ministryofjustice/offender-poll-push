.PHONY: all ecr-login sbt-clean sbt-test sbt-assembly build tag test push clean-remote clean-local

aws_region := eu-west-2
image := hmpps/new-tech-offender-pollpush
sbt_builder_image := circleci/openjdk:8-jdk
# offenderpollpush_version should be passed from command line
all:
	$(MAKE) ecr-login
	$(MAKE) sbt-clean
	$(MAKE) sbt-test
	$(MAKE) sbt-assembly
	$(MAKE) build
	$(MAKE) test
	$(MAKE) push
	$(MAKE) clean-remote
	$(MAKE) clean-local

sbt-clean: build_dir = $(shell pwd)
sbt-clean:
	$(Info Running sbt clean task)
	docker run --rm -v $(build_dir):/build -w /build $(sbt_builder_image) bash -c "sbt -v clean;"

sbt-test: build_dir = $(shell pwd)
sbt-test:
	# Build container runs as root - need to fix up perrms at end so jenkins can clear up the workspace
	docker run --rm -v $(build_dir):/home/circleci/build -w /home/circleci/build $(sbt_builder_image) bash -c "sbt -mem 3072 -v 'set parallelExecution in Test := false' test;"

sbt-assembly: build_dir = $(shell pwd)
sbt-assembly:
	# Build container runs as root - need to fix up perrms at end so jenkins can clear up the workspace
	docker run --rm -v $(build_dir):/home/circleci/build -w /home/circleci/build $(sbt_builder_image) bash -c "sudo mkdir artefacts/; sudo chmod 0777 artefacts; sbt -v -mem 3072 'set test in assembly := {}' 'set target in assembly := file(\"/home/circleci/build/artefacts/\")' assembly; sudo chmod -R 0777 project/"

ecr-login:
	$(shell aws ecr get-login --no-include-email --region ${aws_region})
	aws --region $(aws_region) ecr describe-repositories --repository-names "$(image)" | jq -r .repositories[0].repositoryUri > ecr.repo

build: ecr_repo = $(shell cat ./ecr.repo)
build:
	$(info Build of repo $(ecr_repo))
	docker build -t $(ecr_repo) --build-arg OFFENDERPOLLPUSH_VERSION=${offenderpollpush_version}  -f docker/Dockerfile.aws .

tag: ecr_repo = $(shell cat ./ecr.repo)
tag:
	$(info Tag repo $(ecr_repo) $(offenderpollpush_version))
	docker tag $(ecr_repo) $(ecr_repo):$(offenderpollpush_version)

test: ecr_repo = $(shell cat ./ecr.repo)
test:
	bash -c "GOSS_FILES_STRATEGY=cp GOSS_FILES_PATH="./docker/tests/" GOSS_SLEEP=3 dgoss run -e GOSS_TEST=true $(ecr_repo):latest"

push: ecr_repo = $(shell cat ./ecr.repo)
push:
	docker tag  ${ecr_repo} ${ecr_repo}:${offenderpollpush_version}
	docker push ${ecr_repo}:${offenderpollpush_version}

clean-remote: untagged_images = $(shell aws ecr list-images --region $(aws_region) --repository-name "$(image)" --filter "tagStatus=UNTAGGED" --query 'imageIds[*]' --output json)
clean-remote:
	if [ "${untagged_images}" != "[]" ]; then aws ecr batch-delete-image --region $(aws_region) --repository-name "$(image)" --image-ids '${untagged_images}' || true; fi

clean-local: ecr_repo = $(shell cat ./ecr.repo)
clean-local:
	-docker rmi ${ecr_repo}:latest
	-docker rmi ${ecr_repo}:${offenderpollpush_version}
	-rm -f ./ecr.repo
	-rm -f artefacts/*