# Infrastructure as Code

## Preparation
You must create an OIDC provider, role, and user for the AI to operate in AWS. These commands assume that's already done. You should have come up with values for the following variables and they should be repository secrets in the GitHub repo:

AWS_ROLE_ARN
AWS_REGION
ECR_REPOSITORY
ECS_TASK_DEFINITION
ECS_SERVICE
ECS_CLUSTER

## Creating the Stack
To generate the ECS Cluster, use the command:

```
aws cloudformation deploy \
  --template-file infra/cloudformation.yml \
  --stack-name tempmon2 \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1'
```

If you have to delete and recreate it, use the commands:

```
aws cloudformation delete-stack --stack-name tempmon2 --region us-east-1
aws cloudformation wait stack-delete-complete --stack-name tempmon2 --region us-east-1
aws cloudformation deploy --template-file infra/cloudformation.yml --stack-name tempmon2 --capabilities CAPABILITY_NAMED_IAM --region us-east-1
```
